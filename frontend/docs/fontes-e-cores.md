# Fontes e cores do Inventarium

Levantamento das declarações em frontend/src (.tsx, .ts, .css e .svg). Inclui estilos de estados, hover, foco, gradientes e sombras; não inclui pixels de imagens raster nem estilos internos de bibliotecas. Valores Tailwind obtidos do theme.css instalado, com as substituições de globals.css.

## Fontes

| Fonte | Uso |
|---|---|
| Montserrat | Fonte global: textos, títulos de páginas, botões, formulários e tabelas. Importa pesos 100 a 900, normal e itálico. |
| Linden Hill | Marca Inventarium e classe brand-font. Importa normal e itálico. |
| Arial | Nome Inventarium na barra lateral quando a página tem inventory-reference, em telas de pelo menos 768 px. |
| sans-serif / serif | Famílias de fallback quando a fonte principal não est? disponível. |

## Cores principais

| Papel | Cor |
|---|---|
| Principal e destaque | #5e94e5 |
| Hover principal | #4c82d1 |
| Texto principal | #1e293b |
| Texto secund?rio | #64748b |
| Fundo global | #f8fafc |

## Paleta completa de tokens utilizados

| Token | Valor efetivo | Opacidades explícitas utilizadas |
|---|---|---|
| amber-100 | oklch(96.2% 0.059 95.617) | 70% |
| amber-200 | oklch(92.4% 0.12 95.746) | - |
| amber-50 | oklch(98.7% 0.022 95.277) | - |
| amber-500 | oklch(76.9% 0.188 70.08) | - |
| amber-600 | oklch(66.6% 0.179 58.318) | - |
| amber-700 | oklch(55.5% 0.163 48.998) | - |
| amber-800 | oklch(47.3% 0.137 46.201) | - |
| amber-900 | oklch(41.4% 0.112 45.904) | - |
| black | #000 | 25%, 30%, 40%, 50% |
| blue-100 | #dbeafe | - |
| blue-200 | #bfdbfe | 60% |
| blue-300 | #93c5fd | - |
| blue-400 | #5e94e5 | - |
| blue-50 | #eff6ff | 50%, 60%, 80% |
| blue-500 | #4c82d1 | - |
| blue-600 | #3f70ba | - |
| blue-700 | #315c9d | - |
| blue-800 | #274b80 | - |
| emerald-100 | oklch(95% 0.052 163.051) | - |
| emerald-200 | oklch(90.5% 0.093 164.15) | - |
| emerald-400 | oklch(76.5% 0.177 163.223) | - |
| emerald-50 | oklch(97.9% 0.021 166.113) | - |
| emerald-500 | oklch(69.6% 0.17 162.48) | 20% |
| emerald-600 | oklch(59.6% 0.145 163.225) | - |
| emerald-700 | oklch(50.8% 0.118 165.612) | - |
| emerald-800 | oklch(43.2% 0.095 166.913) | - |
| gray-100 | oklch(96.7% 0.003 264.542) | - |
| gray-200 | oklch(92.8% 0.006 264.531) | 80% |
| gray-300 | oklch(87.2% 0.01 258.338) | - |
| gray-400 | oklch(70.7% 0.022 261.325) | - |
| gray-50 | oklch(98.5% 0.002 247.839) | 50% |
| gray-500 | oklch(55.1% 0.027 264.364) | - |
| gray-600 | oklch(44.6% 0.03 256.802) | - |
| gray-700 | oklch(37.3% 0.034 259.733) | - |
| gray-800 | oklch(27.8% 0.033 256.848) | - |
| gray-900 | oklch(21% 0.034 264.665) | 95% |
| red-100 | oklch(93.6% 0.032 17.717) | - |
| red-200 | oklch(88.5% 0.062 18.334) | - |
| red-400 | oklch(70.4% 0.191 22.216) | - |
| red-50 | oklch(97.1% 0.013 17.38) | - |
| red-500 | oklch(63.7% 0.237 25.331) | - |
| red-600 | oklch(57.7% 0.245 27.325) | - |
| red-700 | oklch(50.5% 0.213 27.518) | - |
| red-800 | oklch(44.4% 0.177 26.899) | - |
| sky-50 | oklch(97.7% 0.013 236.62) | - |
| slate-100 | oklch(96.8% 0.007 247.896) | - |
| slate-200 | oklch(92.9% 0.013 255.508) | 80% |
| slate-300 | oklch(86.9% 0.022 252.894) | - |
| slate-400 | oklch(70.4% 0.04 256.788) | - |
| slate-50 | oklch(98.4% 0.003 247.858) | 50%, 70% |
| slate-500 | oklch(55.4% 0.046 257.417) | - |
| slate-600 | oklch(44.6% 0.043 257.281) | - |
| slate-700 | oklch(37.2% 0.044 257.287) | - |
| slate-800 | oklch(27.9% 0.041 260.031) | - |
| slate-900 | oklch(20.8% 0.042 265.755) | - |
| slate-950 | oklch(12.9% 0.042 264.695) | - |
| transparent | transparent | - |
| white | #fff | 95% |

Tokens /NN aplicam NN% de opacidade à cor; estados sem sufixo usam a cor original. Classes opacity-* também alteram a opacidade do elemento inteiro.

## Valores literais adicionais

| Valor declarado |
|---|
| #16a34a |
| #1e293b |
| #1f3b66 |
| #20314f |
| #20314f06 |
| #274b80 |
| #2878ed |
| #2878ed20 |
| #2f80ff |
| #315c9d |
| #3787e8 |
| #3f70ba |
| #4c82d1 |
| #5E94E5 |
| #5e94e5 |
| #60a5fa |
| #64748b |
| #93c5fd |
| #bfdbfe |
| #dbeafe |
| #dc2626 |
| #dce5f1 |
| #e8eef7 |
| #edf1f6 |
| #ee5970 |
| #eff6ff |
| #f0f4f9 |
| #f5f7fa |
| #f6f8fb |
| #f8fafc |
| #fff0f2 |
| rgba(52,211,153,0.8) |
| rgba(96,165,250,0.4) |

Hexadecimais com 8 dígitos incluem alfa: #2878ed20 (~12,5%) e #20314f06 (~2,4%) são sombras.

## Status do inventário

| Situação | Fundo | Texto | Borda |
|---|---|---|---|
| Em andamento | sky-50 | blue-600 | blue-200 |
| Finalizado | emerald-50 | emerald-600 | emerald-200 |
| Atrasado | red-50 | red-600 | red-200 |

## Arquivos de referência

- frontend/src/app/globals.css
- frontend/src/components/atoms/button/types.ts
- frontend/src/components/atoms/badge/index.tsx
- frontend/src/components/atoms/chip/index.tsx
- frontend/node_modules/tailwindcss/theme.css
