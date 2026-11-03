import test from 'node:test'
import assert from 'node:assert/strict'

import { formatAppVersion } from '../../src/lib/app-version.ts'

test('versão: mantém a tag da release e normaliza o prefixo v', () => {
  assert.equal(formatAppVersion('v7.3.0'), 'v7.3.0')
  assert.equal(formatAppVersion('7.3.0'), 'v7.3.0')
  assert.equal(formatAppVersion('  v7.3.0 '), 'v7.3.0')
})

test('versão: sem valor do CI mostra dev', () => {
  assert.equal(formatAppVersion(undefined), 'dev')
  assert.equal(formatAppVersion(''), 'dev')
  assert.equal(formatAppVersion('   '), 'dev')
})
