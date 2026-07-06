'use client';

import Image from 'next/image';

export function BrandBar() {
  return (
    <div className="flex flex-col items-center gap-1 pb-3 sm:pb-4 border-b mb-4 sm:mb-6" style={{ borderColor: 'var(--border-input)' }}>
      <div className="relative h-10 w-[200px] sm:h-12 sm:w-[260px]">
        <Image
          src="/aq.jpg"
          alt="Aqui Resolve"
          fill
          className="object-contain object-center select-none"
          priority
          sizes="260px"
        />
      </div>
      <p className="text-[11px] sm:text-xs text-text-secondary tracking-wide uppercase">
        Portal de privacidade
      </p>
    </div>
  );
}
