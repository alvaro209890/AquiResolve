"use client"

import { useState } from "react"

interface LogoProps {
  className?: string
  showText?: boolean
}

/** Caminho da logo (arquivo em public/). Usa o SVG que existe no repo; cai no SVG inline se faltar. */
const LOGO_SRC = "/logo-aquiresolve.svg"

export function Logo({ className = "h-8", showText = true }: LogoProps) {
  const [imgError, setImgError] = useState(false)

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <div className="relative flex h-full items-center">
        {!imgError ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={LOGO_SRC}
            alt="AquiResolve"
            className="h-full w-auto object-contain"
            onError={() => setImgError(true)}
          />
        ) : (
          // Marca AquiResolve recriada em SVG (dois ganchos entrelaçados laranja/azul).
          <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-full w-auto" aria-label="AquiResolve">
            <defs>
              <linearGradient id="arOrange" x1="20" y1="20" x2="90" y2="90" gradientUnits="userSpaceOnUse">
                <stop stopColor="#f7941d" />
                <stop offset="1" stopColor="#ef6a1a" />
              </linearGradient>
              <linearGradient id="arBlue" x1="90" y1="90" x2="30" y2="40" gradientUnits="userSpaceOnUse">
                <stop stopColor="#16356b" />
                <stop offset="1" stopColor="#21477f" />
              </linearGradient>
            </defs>
            {/* Gancho laranja (superior-esquerdo) */}
            <path d="M82 38 A30 30 0 1 0 78 86" stroke="url(#arOrange)" strokeWidth="17" strokeLinecap="round" />
            {/* Gancho azul (inferior-direito), entrelaçado */}
            <path d="M38 82 A30 30 0 1 0 42 34" stroke="url(#arBlue)" strokeWidth="17" strokeLinecap="round" />
          </svg>
        )}
      </div>

      {showText && (
        <div className="flex items-baseline gap-0.5">
          <span className="text-xl font-bold text-blue-900">Aqui</span>
          <span className="text-xl font-bold text-orange-500">Resolve</span>
        </div>
      )}
    </div>
  )
}
