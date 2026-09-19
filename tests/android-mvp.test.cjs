const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
test('安卓 MVP 工程包含入口、清单和本地持久化逻辑', () => {
  assert.ok(fs.existsSync('android-mvp/app/src/main/java/com/bianqujian/app/MainActivity.kt'));
  const code = fs.readFileSync('android-mvp/app/src/main/java/com/bianqujian/app/MainActivity.kt', 'utf8');
  assert.match(code, /ACTION_OPEN_DOCUMENT/);
  assert.match(code, /getSharedPreferences/);
  assert.match(code, /codePattern/);
  const wrapper = fs.readFileSync('android-mvp/gradle/wrapper/gradle-wrapper.properties', 'utf8');
  assert.match(wrapper, /gradle-9\.3\.0-bin\.zip/);
});
