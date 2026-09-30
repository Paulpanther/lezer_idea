// Runs @lezer/generator on the grammar read from stdin and prints its errors and warnings as JSON.
// Nothing is written to disk. Usage: node lezer-check.cjs <@lezer/generator package directory> <file name>
const [generatorDir, fileName] = process.argv.slice(2)
const {buildParserFile, GenError} = require(generatorDir)

let text = ""
process.stdin.setEncoding("utf8")
process.stdin.on("data", chunk => text += chunk)
process.stdin.on("end", () => {
  const result = {errors: [], warnings: [], failure: null}
  try {
    buildParserFile(text, {fileName, warn: message => result.warnings.push(message)})
  } catch (e) {
    if (e instanceof GenError) result.errors.push(e.message)
    else result.failure = e && e.stack ? e.stack : String(e)
  }
  process.stdout.write(JSON.stringify(result))
})
