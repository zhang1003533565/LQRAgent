/**
 * ThemeScript - pins the app to the single supported light theme before hydration.
 */
export default function ThemeScript() {
  const themeScript = `
    (function() {
      try {
        document.documentElement.classList.remove('dark', 'theme-glass', 'theme-snow');
        localStorage.setItem('deeptutor-theme', 'light');
      } catch (e) {
        /* localStorage may be disabled */
      }
    })();
  `;

  return (
    <script
      dangerouslySetInnerHTML={{ __html: themeScript }}
      suppressHydrationWarning
    />
  );
}
