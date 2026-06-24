"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { BookOpen, Lightbulb, Cloud } from "lucide-react"
import { AssistantChat } from "@/components/manual/assistant-chat"
import { SECTIONS, CONCEPTS, INFRA } from "@/lib/manual-content"

// O conteúdo da documentação foi extraído para `lib/manual-content.ts` (fonte única),
// compartilhado com o Copiloto IA (app/api/assistant). Esta página apenas o renderiza.

export default function ManualPage() {
  return (
    <div className="space-y-6 p-4 md:p-6 max-w-4xl">
      {/* Cabeçalho */}
      <div className="flex items-center gap-3">
        <div className="rounded-lg bg-indigo-100 dark:bg-indigo-900/40 p-2 text-indigo-700 dark:text-indigo-300">
          <BookOpen className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">Manual do Painel</h1>
          <p className="text-sm text-muted-foreground">
            Guia completo de cada área do painel AquiResolve, o que faz e como reflete no app.
          </p>
        </div>
      </div>

      {/* Copiloto IA — pergunte como fazer algo no painel */}
      <AssistantChat />

      {/* Índice */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Índice</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {SECTIONS.map((s) => (
              <a
                key={s.id}
                href={`#${s.id}`}
                className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <s.icon className="h-4 w-4 shrink-0" />
                {s.title}
              </a>
            ))}
            <a
              href="#conceitos"
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <Lightbulb className="h-4 w-4 shrink-0" />
              Conceitos importantes
            </a>
            <a
              href="#infra"
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <Cloud className="h-4 w-4 shrink-0" />
              Infraestrutura
            </a>
          </div>
        </CardContent>
      </Card>

      {/* Seções */}
      {SECTIONS.map((section) => (
        <Card key={section.id} id={section.id} className="scroll-mt-20">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <section.icon className="h-5 w-5 text-primary" />
              {section.title}
            </CardTitle>
            {section.intro && <p className="text-sm text-muted-foreground">{section.intro}</p>}
          </CardHeader>
          <CardContent className="space-y-4">
            {section.items.map((item) => (
              <div key={item.title} className="border-l-2 border-muted pl-4">
                <p className="font-medium">{item.title}</p>
                <p className="mt-1 text-sm text-muted-foreground">{item.body}</p>
                {item.steps && (
                  <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
                    {item.steps.map((step, i) => (
                      <li key={i}>{step}</li>
                    ))}
                  </ol>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      ))}

      {/* Conceitos */}
      <Card id="conceitos" className="scroll-mt-20">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Lightbulb className="h-5 w-5 text-amber-500" />
            Conceitos importantes
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {CONCEPTS.map((c) => (
            <div key={c.title} className="border-l-2 border-amber-200 pl-4">
              <p className="font-medium">{c.title}</p>
              <p className="mt-1 text-sm text-muted-foreground">{c.body}</p>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Infraestrutura */}
      <Card id="infra" className="scroll-mt-20">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Cloud className="h-5 w-5 text-sky-500" />
            Infraestrutura
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            Onde cada parte roda. As variáveis e chaves completas ficam na pasta local
            <code className="mx-1 rounded bg-muted px-1 py-0.5 text-xs">infra-config/</code>
            (na raiz do projeto, não versionada).
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          {INFRA.map((i) => (
            <div key={i.title} className="flex gap-3">
              <i.icon className="mt-0.5 h-5 w-5 shrink-0 text-sky-600" />
              <div>
                <p className="font-medium">{i.title}</p>
                <p className="mt-1 text-sm text-muted-foreground">{i.body}</p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <p className="pb-4 text-center text-xs text-muted-foreground/60">
        AquiResolve Admin · Manual atualizado conforme novas áreas são adicionadas ao painel.
      </p>
    </div>
  )
}
