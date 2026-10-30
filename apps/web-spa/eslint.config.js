import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'coverage'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: { ecmaVersion: 2022, globals: globals.browser },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // `_campo` descarta propriedades de propósito ao montar respostas.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', ignoreRestSiblings: true },
      ],
    },
  },
  {
    // Arquivo de rotas e providers não é módulo de componente para o fast refresh.
    files: [
      'src/router.tsx',
      'src/auth/AuthProvider.tsx',
      'src/features/client/activeReservation.tsx',
      'src/features/organizer/shared.tsx',
      'src/features/operator/OperatorProvider.tsx',
      'src/features/operator/scanUi.tsx',
    ],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
);
