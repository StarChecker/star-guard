// Тема для страниц расширения (popup, настройки, preview): там нет CSS-переменных GitHub,
// поэтому задаём значения Primer для светлой и тёмной темы сами.
(function () {
  'use strict';
  const LIGHT = {
    '--fgColor-default': '#1f2328', '--fgColor-muted': '#59636e', '--fgColor-accent': '#0969da',
    '--fgColor-success': '#1a7f37', '--fgColor-attention': '#9a6700', '--fgColor-severe': '#bc4c00',
    '--fgColor-danger': '#d1242f', '--borderColor-default': '#d1d9e0', '--borderColor-muted': '#d1d9e0b3',
    '--bgColor-default': '#ffffff', '--bgColor-muted': '#f6f8fa',
  };
  const DARK = {
    '--fgColor-default': '#f0f6fc', '--fgColor-muted': '#9198a1', '--fgColor-accent': '#4493f8',
    '--fgColor-success': '#3fb950', '--fgColor-attention': '#d29922', '--fgColor-severe': '#db6d28',
    '--fgColor-danger': '#f85149', '--borderColor-default': '#3d444d', '--borderColor-muted': '#3d444db3',
    '--bgColor-default': '#0d1117', '--bgColor-muted': '#151b23',
  };
  function apply(theme) {
    const dark = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
    const vars = dark ? DARK : LIGHT;
    for (const [key, value] of Object.entries(vars)) document.documentElement.style.setProperty(key, value);
    document.documentElement.style.background = vars['--bgColor-default'];
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  }
  apply('auto');
  window.StarGuardTheme = { apply, LIGHT, DARK };
})();
