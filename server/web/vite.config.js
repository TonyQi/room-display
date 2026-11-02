import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import fs from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
// root 必须用 native realpath，与 Rollup 模块 id 保持同一真实路径，否则经 junction 访问时产物名会变成绝对路径
const root = fs.realpathSync.native(dirname(fileURLToPath(import.meta.url)));
export default defineConfig({root,plugins:[vue()],build:{outDir:resolve(root,'../target/classes/static'),emptyOutDir:true},server:{proxy:{'/api':'http://127.0.0.1:8080'}}});
