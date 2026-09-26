// Standard emoji drawn in Noto, served as /emoji/<code points>.svg, one file per emoji
// (architecture ADR-010). Everything but the flags comes from @iconify-json/noto. Noto keeps
// its flags apart, so they are fetched from the noto-emoji repository at one pinned commit and
// kept in node_modules/.cache, which is what lets a second build run without the network.
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs'
import {createRequire} from 'node:module'
import {join} from 'node:path'

const require = createRequire(import.meta.url)

const NOTO_COMMIT = 'e20cbc2bbec1926686be9f9bee7d1d2cfa1fea0e'
const NOTO_RAW = `https://raw.githubusercontent.com/googlefonts/noto-emoji/${NOTO_COMMIT}`
const FLAGS_AT = 'third_party/region-flags/waved-svg'
const LICENCES = {
    'LICENSE-noto.txt': '2D/svg/LICENSE',
    'LICENSE-flags.txt': 'third_party/region-flags/LICENSE',
}

// Code points in hex, four digits at least, without the variation selector. emojiFile in
// src/plugins/emojiArt.ts names the files the same way; change one, change the other.
const fileOf = hexcode => hexcode.toLowerCase().split('-')
    .filter(point => point !== 'fe0f')
    .map(point => point.padStart(4, '0'))
    .join('-')

const iconifyEmoji = () => {
    const chars = require('@iconify-json/noto/chars.json')
    const {icons, aliases = {}, width = 128, height = 128} = require('@iconify-json/noto/icons.json')
    const bodyOf = name => icons[name] ?? bodyOf(aliases[name].parent)
    const files = new Map()
    for (const [hexcode, name] of Object.entries(chars)) {
        const icon = bodyOf(name)
        const box = `0 0 ${icon.width ?? width} ${icon.height ?? height}`
        files.set(`${fileOf(hexcode)}.svg`, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${box}">${icon.body}</svg>`)
    }
    return files
}

const flagHexcodes = () => require('emojibase-data/en/compact.json')
    .map(one => one.hexcode)
    .filter(hexcode => /^1F1[0-9A-F]{2}-1F1[0-9A-F]{2}$|^1F3F4-E00/.test(hexcode))

const fetchText = async (path, attempts = 3) => {
    for (let attempt = 1; ; attempt++) {
        try {
            const response = await fetch(`${NOTO_RAW}/${path}`)
            if (response.status === 404) return undefined
            if (!response.ok) throw new Error(`${response.status} for ${path}`)
            return await response.text()
        } catch (error) {
            if (attempt >= attempts) throw error
        }
    }
}

// A flag that is another's (Bouvet Island is Norway's) is stored in git as a link, which the
// raw host serves as the name of the file it points at.
const fetchFlag = async (name) => {
    const text = await fetchText(`${FLAGS_AT}/${name}`)
    if (text === undefined || text.trimStart().startsWith('<')) return text
    return fetchText(`${FLAGS_AT}/${text.trim()}`)
}

const cachedFlags = async (cacheDir) => {
    const dir = join(cacheDir, NOTO_COMMIT)
    const done = join(dir, '.complete')
    const files = new Map()
    if (!existsSync(done)) {
        mkdirSync(dir, {recursive: true})
        const wanted = flagHexcodes()
        for (let at = 0; at < wanted.length; at += 16) {
            await Promise.all(wanted.slice(at, at + 16).map(async (hexcode) => {
                const svg = await fetchFlag(`emoji_u${hexcode.toLowerCase().replaceAll('-', '_')}.svg`)
                if (svg !== undefined) writeFileSync(join(dir, `${fileOf(hexcode)}.svg`), svg)
            }))
        }
        for (const [file, path] of Object.entries(LICENCES)) {
            const text = await fetchText(path)
            if (!text) throw new Error(`No licence at ${path} in noto-emoji ${NOTO_COMMIT}`)
            writeFileSync(join(dir, file), text)
        }
        writeFileSync(done, '')
    }
    for (const hexcode of flagHexcodes()) {
        const file = `${fileOf(hexcode)}.svg`
        if (existsSync(join(dir, file))) files.set(file, readFileSync(join(dir, file), 'utf8'))
    }
    for (const file of Object.keys(LICENCES)) files.set(file, readFileSync(join(dir, file), 'utf8'))
    // The Noto notice names the Apache licence without holding it, and the licence asks to
    // travel with the work.
    files.set('LICENSE-apache-2.0.txt', readFileSync(new URL('./Apache-2.0.txt', import.meta.url), 'utf8'))
    return files
}

export const notoEmoji = ({cacheDir}) => {
    let built
    let served
    return {
        name: 'noto-emoji',
        async generateBundle() {
            built ??= new Map([...iconifyEmoji(), ...await cachedFlags(cacheDir)])
            for (const [file, source] of built) {
                this.emitFile({type: 'asset', fileName: `emoji/${file}`, source})
            }
        },
        configureServer(server) {
            // A dev server with no network still serves every emoji but the flags.
            served ??= cachedFlags(cacheDir)
                .catch(() => new Map())
                .then(flags => new Map([...iconifyEmoji(), ...flags]))
            server.middlewares.use('/emoji/', async (request, response) => {
                const file = decodeURIComponent((request.url ?? '').replace(/^\//, '').split('?')[0])
                const source = (await served).get(file)
                if (source === undefined) {
                    response.statusCode = 404
                    response.end()
                    return
                }
                response.setHeader('Content-Type', file.endsWith('.svg') ? 'image/svg+xml' : 'text/plain')
                response.end(source)
            })
        },
    }
}
