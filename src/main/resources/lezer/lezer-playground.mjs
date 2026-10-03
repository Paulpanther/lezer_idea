// Worker of the Lezer Playground tool window: builds parsers from grammars and parses example input with them.
// Reads one JSON request per line from stdin and writes one JSON response per line to stdout.
//
// Request: {id, generatorModule, grammar, grammarDir, input, top?, dialect?, externals: [{kind, name, source}]}
// Response: {id, tree, grammarError?, moduleErrors, modules, truncated, ms}
//   modules: the files of all @external implementations imported so far
//   tree: {n: name, f: from, t: to, e?: true for error nodes, c?: children}
import {createInterface} from "node:readline"
import {createRequire} from "node:module"
import {pathToFileURL} from "node:url"
import fs from "node:fs"
import path from "node:path"

// User modules may log, which must not end up in the responses
const write = process.stdout.write.bind(process.stdout)
for (const method of ["log", "info", "debug", "warn"]) console[method] = (...args) => process.stderr.write(args.join(" ") + "\n")

const MAX_NODES = 10000

/** The ESM entry of a package resolved from `fromFile`, so modules share instances with the user's code. */
function resolveEsm(specifier, fromFile) {
  const cjs = createRequire(fromFile).resolve(specifier)
  let dir = path.dirname(cjs)
  for (;;) {
    const file = path.join(dir, "package.json")
    if (fs.existsSync(file)) {
      const pkg = JSON.parse(fs.readFileSync(file, "utf8"))
      if (pkg.name === specifier) {
        const exp = pkg.exports?.["."] ?? pkg.exports
        const entry = typeof exp === "string" ? exp : exp?.import ?? exp?.default ?? pkg.module ?? pkg.main
        return entry ? path.join(dir, typeof entry === "string" ? entry : entry.default) : cjs
      }
    }
    const parent = path.dirname(dir)
    if (parent === dir) return cjs
    dir = parent
  }
}

// Node caches imported modules, the worker is restarted when one of them changes
async function importFile(file) {
  return import(pathToFileURL(file).href)
}

/** The file of a module named in `@external … from "source"`, relative to the grammar. */
function resolveSource(source, grammarDir) {
  if (!source.startsWith(".") && !path.isAbsolute(source)) return resolveEsm(source, path.join(grammarDir, "grammar.js"))
  const base = path.resolve(grammarDir, source)
  const candidates = [base, ...[".js", ".mjs", ".cjs", ".ts", ".mts"].map(ext => base + ext),
    path.join(base, "index.js"), path.join(base, "index.ts")]
  const file = candidates.find(f => fs.existsSync(f) && fs.statSync(f).isFile())
  if (!file) throw new Error(`Cannot find module '${source}'`)
  return file
}

let cached = {key: null, parser: null, moduleErrors: []}
// All modules imported by this process, node keeps them cached until it exits
const importedModules = new Set()

async function build(request) {
  const {generatorModule: generatorEntry, grammar, grammarDir, externals = []} = request
  const moduleErrors = []

  // Load the modules named by the grammar, keyed by file
  const files = new Map()
  const load = async file => {
    if (!files.has(file)) {
      try { files.set(file, await importFile(file)) } catch (e) { files.set(file, null); moduleErrors.push(`${file}: ${e.message}`) }
    }
    return files.get(file)
  }
  const exportsByName = new Map()
  for (const {name, source} of externals) {
    let file
    try { file = resolveSource(source, grammarDir) } catch (e) { moduleErrors.push(`${source}: ${e.message}`); continue }
    const mod = await load(file)
    if (!mod) continue
    if (name in mod) exportsByName.set(name, mod[name])
    else moduleErrors.push(`${path.basename(file)} doesn't export '${name}'`)
  }

  for (const file of files.keys()) importedModules.add(file)
  const key = JSON.stringify([grammar, generatorEntry, externals, [...files.keys()].map(f => fs.statSync(f).mtimeMs)])
  if (cached.key === key) return cached

  const generator = await import(pathToFileURL(generatorEntry).href)
  const lr = await import(pathToFileURL(resolveEsm("@lezer/lr", generatorEntry)).href)
  const common = await import(pathToFileURL(resolveEsm("@lezer/common", generatorEntry)).href)
  const found = name => exportsByName.get(name)

  const parser = generator.buildParser(grammar, {
    warn: () => {},
    // Placeholders keep grammars with missing implementations usable
    externalTokenizer: name => found(name) ?? new lr.ExternalTokenizer(() => {}),
    externalSpecializer: name => found(name) ?? (() => -1),
    externalPropSource: name => found(name) ?? (() => null),
    externalProp: name => found(name) ?? new common.NodeProp(),
    // The generator works without a context tracker, so a missing one needs no placeholder
    contextTracker: found(externals.find(e => e.kind === "context")?.name),
  })

  cached = {key, parser, moduleErrors}
  return cached
}

function toJson(tree, budget) {
  const cursor = tree.cursor()
  const visit = () => {
    const node = {n: cursor.type.name, f: cursor.from, t: cursor.to}
    if (cursor.type.isError) node.e = true
    if (budget.left-- > 0 && cursor.firstChild()) {
      node.c = []
      do { node.c.push(visit()) } while (budget.left > 0 && cursor.nextSibling())
      cursor.parent()
    }
    return node
  }
  return visit()
}

async function handle(request) {
  const start = Date.now()
  const response = {id: request.id, tree: null, moduleErrors: [], modules: [], truncated: false}
  let built
  try {
    built = await build(request)
  } catch (e) {
    response.grammarError = e?.message ?? String(e)
    response.modules = [...importedModules]
    return response
  }
  response.moduleErrors = built.moduleErrors
  response.modules = [...importedModules]
  try {
    const options = {}
    if (request.top) options.top = request.top
    if (request.dialect) options.dialect = request.dialect
    const tree = built.parser.configure(options).parse(request.input)
    const budget = {left: MAX_NODES}
    response.tree = toJson(tree, budget)
    response.truncated = budget.left <= 0
  } catch (e) {
    response.grammarError = e?.message ?? String(e)
  }
  response.ms = Date.now() - start
  return response
}

// Requests are handled one after another
let queue = Promise.resolve()
createInterface({input: process.stdin}).on("line", line => {
  queue = queue.then(async () => {
    let request
    try { request = JSON.parse(line) } catch { return }
    let response
    try {
      response = await handle(request)
    } catch (e) {
      response = {id: request.id, tree: null, moduleErrors: [], truncated: false, grammarError: String(e?.stack ?? e)}
    }
    write(JSON.stringify(response) + "\n")
  })
})
