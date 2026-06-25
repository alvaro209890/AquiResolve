"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { collection, onSnapshot, orderBy, query, limit as firestoreLimit } from "firebase/firestore"
import { Bell, BellOff, CheckCheck, Package, AlertTriangle, XCircle, CreditCard } from "lucide-react"
import { db } from "@/lib/firebase"
import { useAuth } from "@/components/auth-provider"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { cn } from "@/lib/utils"

type NotifKind = "new_order" | "awaiting_payment" | "needs_provider" | "cancelled" | "completed"

interface NotifItem {
  id: string
  orderId: string
  kind: NotifKind
  title: string
  desc: string
  ts: number
  href: string
}

const KIND_META: Record<NotifKind, { icon: typeof Package; tone: string }> = {
  new_order: { icon: Package, tone: "text-blue-600 dark:text-blue-400" },
  awaiting_payment: { icon: CreditCard, tone: "text-amber-600 dark:text-amber-400" },
  needs_provider: { icon: AlertTriangle, tone: "text-amber-600 dark:text-amber-400" },
  cancelled: { icon: XCircle, tone: "text-red-600 dark:text-red-400" },
  completed: { icon: CheckCheck, tone: "text-emerald-600 dark:text-emerald-400" },
}

// Estados que merecem atenção imediata do operador.
const NEEDS_PROVIDER = new Set(["pending", "distributing", "searching_provider"])
const AWAITING = new Set(["awaiting_payment"])

function asMillis(v: unknown): number {
  if (!v) return Date.now()
  const anyV = v as { toMillis?: () => number; seconds?: number }
  if (typeof anyV.toMillis === "function") return anyV.toMillis()
  if (typeof anyV.seconds === "number") return anyV.seconds * 1000
  const n = Date.parse(String(v))
  return Number.isFinite(n) ? n : Date.now()
}

// Bipe curto via WebAudio (sem assets) — som ao cair uma notificação.
function playBeep() {
  try {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.type = "sine"
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.0001, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.22, ctx.currentTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.45)
    osc.start()
    osc.stop(ctx.currentTime + 0.45)
    osc.onended = () => ctx.close().catch(() => null)
  } catch {
    /* som é best-effort */
  }
}

function shortId(id: string) {
  return id.slice(-6).toUpperCase()
}

function timeAgo(ts: number) {
  const diff = Math.max(0, Date.now() - ts)
  const min = Math.floor(diff / 60000)
  if (min < 1) return "agora"
  if (min < 60) return `há ${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `há ${h} h`
  return `há ${Math.floor(h / 24)} d`
}

export function NotificationBell() {
  const { user, loading: authLoading } = useAuth()
  const router = useRouter()
  const [items, setItems] = useState<NotifItem[]>([])
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [soundOn, setSoundOn] = useState(true)

  const statuses = useRef<Map<string, string>>(new Map())
  const bootstrapped = useRef(false)

  // Preferência de som persistida.
  useEffect(() => {
    try {
      const saved = localStorage.getItem("notif-sound")
      if (saved === "off") setSoundOn(false)
    } catch {
      /* ignore */
    }
  }, [])

  const toggleSound = () => {
    setSoundOn((v) => {
      const next = !v
      try {
        localStorage.setItem("notif-sound", next ? "on" : "off")
      } catch {
        /* ignore */
      }
      return next
    })
  }

  useEffect(() => {
    if (authLoading || !user) return
    let unsub = () => {}
    try {
      const q = query(collection(db, "orders"), orderBy("createdAt", "desc"), firestoreLimit(60))
      unsub = onSnapshot(
        q,
        (snap) => {
          // Primeira carga: estabelece a linha de base sem notificar.
          if (!bootstrapped.current) {
            snap.forEach((d) => statuses.current.set(d.id, String((d.data() as { status?: string }).status ?? "")))
            bootstrapped.current = true
            return
          }

          const fresh: NotifItem[] = []
          for (const change of snap.docChanges()) {
            const id = change.doc.id
            const data = change.doc.data() as Record<string, unknown>
            const status = String(data.status ?? "")
            const prev = statuses.current.get(id)
            const ts = asMillis(data.createdAt ?? data.updatedAt)
            const service = String(data.serviceCategory ?? data.serviceType ?? data.category ?? "Serviço")

            if (change.type === "added" && prev === undefined) {
              fresh.push({
                id: `${id}:new`,
                orderId: id,
                kind: AWAITING.has(status) ? "awaiting_payment" : "new_order",
                title: `Novo pedido · #${shortId(id)}`,
                desc: service,
                ts,
                href: "/dashboard/controle/monitoramento",
              })
            } else if (change.type === "modified" && prev !== undefined && prev !== status) {
              if (AWAITING.has(status)) {
                fresh.push({ id: `${id}:${status}`, orderId: id, kind: "awaiting_payment", title: `Aguardando pagamento · #${shortId(id)}`, desc: service, ts, href: "/dashboard/controle/monitoramento" })
              } else if (NEEDS_PROVIDER.has(status)) {
                fresh.push({ id: `${id}:${status}`, orderId: id, kind: "needs_provider", title: `Buscando prestador · #${shortId(id)}`, desc: service, ts, href: "/dashboard/controle/monitoramento" })
              } else if (status === "cancelled") {
                fresh.push({ id: `${id}:${status}`, orderId: id, kind: "cancelled", title: `Pedido cancelado · #${shortId(id)}`, desc: service, ts, href: "/orders" })
              } else if (status === "completed") {
                fresh.push({ id: `${id}:${status}`, orderId: id, kind: "completed", title: `Pedido finalizado · #${shortId(id)}`, desc: service, ts, href: "/orders" })
              }
            }

            statuses.current.set(id, status)
          }

          if (fresh.length > 0) {
            setItems((cur) => {
              const seen = new Set(cur.map((i) => i.id))
              const novos = fresh.filter((f) => !seen.has(f.id))
              if (novos.length === 0) return cur
              return [...novos, ...cur].slice(0, 30)
            })
            setUnread((u) => u + fresh.length)
            if (soundOn) playBeep()
          }
        },
        () => {
          /* listener best-effort — falha silenciosa não derruba o header */
        }
      )
    } catch {
      /* ignore */
    }
    return () => unsub()
    // soundOn lido por ref-effect: re-subscrição barata e evita stale closure
  }, [authLoading, user, soundOn])

  const handleOpenChange = (o: boolean) => {
    setOpen(o)
    if (o) setUnread(0)
  }

  const badge = useMemo(() => (unread > 9 ? "9+" : String(unread)), [unread])

  return (
    <DropdownMenu open={open} onOpenChange={handleOpenChange}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="h-9 w-9 p-0 text-muted-foreground hover:text-foreground relative"
          aria-label="Notificações"
        >
          <Bell className="h-4 w-4" />
          {unread > 0 && (
            <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center leading-none">
              {badge}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-80 p-0 animate-scale-in">
        <div className="flex items-center justify-between px-3 py-2">
          <DropdownMenuLabel className="p-0 text-sm font-semibold">Notificações</DropdownMenuLabel>
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault()
              toggleSound()
            }}
            className="text-muted-foreground hover:text-foreground p-1 rounded-md hover:bg-muted/60"
            title={soundOn ? "Silenciar som" : "Ativar som"}
            aria-label={soundOn ? "Silenciar som das notificações" : "Ativar som das notificações"}
          >
            {soundOn ? <Bell className="h-3.5 w-3.5" /> : <BellOff className="h-3.5 w-3.5" />}
          </button>
        </div>
        <DropdownMenuSeparator className="my-0" />

        <div className="max-h-[360px] overflow-y-auto">
          {items.length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-muted-foreground">
              Nenhuma notificação por enquanto.
              <br />
              Você será avisado quando novos pedidos chegarem.
            </div>
          ) : (
            items.map((n) => {
              const Meta = KIND_META[n.kind]
              const Icon = Meta.icon
              return (
                <button
                  key={n.id}
                  type="button"
                  onClick={() => {
                    setOpen(false)
                    router.push(n.href)
                  }}
                  className="w-full flex items-start gap-3 px-3 py-2.5 text-left hover:bg-muted/60 transition-colors border-b border-border/40 last:border-b-0"
                >
                  <span className={cn("mt-0.5 shrink-0", Meta.tone)}>
                    <Icon className="h-4 w-4" />
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block text-sm font-medium text-foreground truncate">{n.title}</span>
                    <span className="block text-xs text-muted-foreground truncate">{n.desc}</span>
                  </span>
                  <span className="text-[10px] text-muted-foreground whitespace-nowrap mt-0.5">{timeAgo(n.ts)}</span>
                </button>
              )
            })
          )}
        </div>

        {items.length > 0 && (
          <>
            <DropdownMenuSeparator className="my-0" />
            <button
              type="button"
              onClick={() => {
                setOpen(false)
                router.push("/dashboard/controle/monitoramento")
              }}
              className="w-full px-3 py-2 text-center text-xs font-medium text-primary hover:bg-muted/60"
            >
              Ver monitoramento de pedidos
            </button>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
