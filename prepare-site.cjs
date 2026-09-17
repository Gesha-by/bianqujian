const fs = require('node:fs');
fs.mkdirSync('public', {recursive:true});
for (const name of ['index.html', 'import-utils.js']) {
  fs.copyFileSync(name, 'public/' + name);
}
