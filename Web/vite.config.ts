import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { name } from "./package.json"

const genBaseUrl = (mode: string) => {
  if (mode == "production") {
    return `/${name}/`
  }
  return "/"
}


export default defineConfig(({ mode }) => {
  return {
    base: genBaseUrl(mode),
    plugins: [
      react()
    ],
  }
})
