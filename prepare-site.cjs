const fs = require('node:fs');
fs.mkdirSync('out', {recursive:true});
for (const name of ['index.html', 'import-utils.js']) {
  fs.copyFileSync(name, 'out/' + name);
}
