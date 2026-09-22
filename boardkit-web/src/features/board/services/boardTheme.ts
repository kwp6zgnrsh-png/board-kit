export interface BoardTheme {
  accent: string
  accentStrong: string
  soft: string
}

const BOARD_THEMES: BoardTheme[] = [
  {
    accent: '#62c4ff',
    accentStrong: '#147df5',
    soft: '#e8f7ff',
  },
  {
    accent: '#ff8ec8',
    accentStrong: '#e83e8c',
    soft: '#fff0f7',
  },
  {
    accent: '#62df91',
    accentStrong: '#14a95b',
    soft: '#edfff3',
  },
  {
    accent: '#ffe348',
    accentStrong: '#e7b900',
    soft: '#fffbe4',
  },
  {
    accent: '#ad82ff',
    accentStrong: '#7547db',
    soft: '#f5efff',
  },
  {
    accent: '#ff7d70',
    accentStrong: '#e84738',
    soft: '#fff0ee',
  },
]

export function getBoardTheme(slug: string): BoardTheme {
  const hash = [...slug].reduce(
    (value, character) => value + character.charCodeAt(0),
    0,
  ) + slug.length * 2

  return BOARD_THEMES[hash % BOARD_THEMES.length]!
}
