const {test} = require('node:test');
const assert = require('node:assert/strict');
const u = require('../import-utils.js');
test('无取件码时不生成假包裹', () => assert.deepEqual(u.parse('订单交易完成'), []));
test('长文本提取多个码并去重，不猜测商品名', () => assert.deepEqual(u.parse('A-302-8\nB-127-6\nA-302-8'), [{name:'未提供商品名',shelf:'A-302-8'},{name:'未提供商品名',shelf:'B-127-6'}]));
test('图片文字作为文本显示而非执行 HTML', () => assert.equal(u.escape('<img src=x>'), '&lt;img src=x&gt;'));
