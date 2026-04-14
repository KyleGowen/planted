This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

### Access from other devices on your home network (LAN)

1. Find your machine’s LAN IP (for example `192.168.1.10`).
2. Run the dev server so it listens on all interfaces: `npm run dev:lan` (same as `next dev --hostname 0.0.0.0` on this Next.js version).
3. In development, API requests go to the same host as the UI (`/api/...` on port 3000) and Next.js proxies them to Spring on `127.0.0.1:8080`, so you do **not** need `NEXT_PUBLIC_API_URL` for LAN. (Only set it if you intentionally want the browser to call a different API origin.) Next.js 16 blocks some cross-origin dev traffic by default; this repo sets `allowedDevOrigins` from your machine’s private IPv4 addresses at dev-server startup. Override with `PLANTED_DEV_EXTRA_ORIGINS=host:3000,other:3000` if needed.
4. On another device, open `http://<YOUR_LAN_IP>:3000`.

Plant **images** use root-relative `/images/...` URLs in local dev; Next.js rewrites those to Spring, so LAN phones work without `planted.storage.public-base-url`. Set that property only if a client loads images from Spring directly (no `/images` proxy). Do not configure WAN port forwarding if you want to stay off the public internet.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
