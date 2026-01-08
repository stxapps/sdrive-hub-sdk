const { defineConfig } = require('eslint/config');
const { FlatCompat } = require('@eslint/eslintrc');
const js = require('@eslint/js');

const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
});

module.exports = defineConfig([
  {
    ignores: ['build', '.expo', 'android', 'ios', 'node_modules'],
  },
  ...compat.extends('universe/native'),
  {
    rules: {
      'prettier/prettier': [
        'warn',
        {
          singleQuote: true,
        },
      ],
    },
  },
]);
