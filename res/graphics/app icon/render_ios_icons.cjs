#!/usr/bin/env node
// Optional asset-generation tool. Pass a Sharp module directory as the first argument.
// The application builds use the checked-in PNG files and do not require Node.js.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.argv[2] ? path.resolve(process.argv[2]) : 'sharp');

function svgBody(filename) {
    return fs.readFileSync(path.join(__dirname, filename), 'utf8')
        .replace(/^[\s\S]*?<svg\b[^>]*>/, '')
        .replace(/<\/svg>\s*$/, '');
}

// Android foregrounds include adaptive-icon padding. Use the original geometry on iOS.
function foreground(filename) {
    return svgBody(filename).replace('transform="matrix(.55867 0 0 .55867 113 128.39)"', '');
}

function svg(background, content) {
    return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1024" height="1024" viewBox="0 0 512 512">${background}${content}</svg>`;
}

async function main() {
    const color = foreground('launcher_foreground.svg');
    const monochrome = `<g fill="#fff">${foreground('launcher_foreground_monochrome.svg')}</g>`;
    const variants = [
        ['app-icon-1024.png', svg(svgBody('launcher_background.svg'), color)],
        ['app-icon-1024-dark.png', svg('<rect width="512" height="512" fill="#1c1c1e"/>', color)],
        ['app-icon-1024-grayscale.png', svg('<rect width="512" height="512" fill="#000"/>', monochrome)],
    ];
    const output = path.resolve(__dirname, '../../../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset');
    const rendered = await Promise.all(variants.map(async ([name, source]) => [name,
        await sharp(Buffer.from(source)).removeAlpha().toColourspace('srgb').png().toBuffer(),
    ]));
    for (const [name, png] of rendered) {
        const metadata = await sharp(png).metadata();
        if (metadata.width !== 1024 || metadata.height !== 1024 || metadata.hasAlpha) {
            throw new Error(`Invalid iOS icon: ${name}`);
        }
        fs.writeFileSync(path.join(output, name), png);
        console.log(`${name}: ${metadata.width}x${metadata.height}, ${metadata.channels} channels, opaque`);
    }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
