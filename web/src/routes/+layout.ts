// Client-rendered SPA: the UI talks to the server at runtime over HTTP + the live stream, so there is
// no server render and nothing to prerender. The static adapter ships the shell + an index.html fallback.
export const ssr = false;
export const prerender = false;
