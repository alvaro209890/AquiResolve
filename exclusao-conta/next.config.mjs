/** @type {import('next').NextConfig} */
const nextConfig = {
  // Impede o Next.js de tentar bundlar o firebase-admin para o cliente
  serverExternalPackages: ['firebase-admin', 'firebase-admin/app', 'firebase-admin/auth', 'firebase-admin/firestore', 'firebase-admin/storage'],
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
}

export default nextConfig
