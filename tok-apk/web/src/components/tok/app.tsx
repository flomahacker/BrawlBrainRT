import { useEffect, useState } from "react";
import { BarChart3, Flame, Route, UserRound, Zap } from "lucide-react";
import { formatNum, knownZones, plural, titleName, todayISO, type Shift, type ShiftDraft } from "@/game/model";
import { playSound } from "@/game/sound";
import { useGame, useTok } from "@/game/store";
import { BikeMark } from "@/components/tok/mark";
import { FanfareModal } from "@/components/tok/fanfare";
import { ShiftForm } from "@/components/tok/form";
import { PathView } from "@/components/tok/path";
import { ProfileView } from "@/components/tok/profile";
import { StatsView } from "@/components/tok/stats";
import { TodayView } from "@/components/tok/today";
import { Sheet } from "@/components/tok/ui";
import { cn } from "@/lib/cn";

type Tab = "today" | "stats" | "path" | "me";

const TABS: { id: Tab; label: string; icon: typeof Zap }[] = [
  { id: "today", label: "Смена", icon: Zap },
  { id: "stats", label: "Разбор", icon: BarChart3 },
  { id: "path", label: "Путь", icon: Route },
  { id: "me", label: "Ты", icon: UserRound },
];

type Editor = { mode: "create"; draft: ShiftDraft } | { mode: "edit"; id: string; draft: ShiftDraft };

export function App() {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const unsub = useTok.persist.onFinishHydration(() => setReady(true));
    void useTok.persist.rehydrate();
    return () => {
      unsub();
    };
  }, []);
  if (!ready) return <Splash />;
  return <Shell />;
}

function Splash() {
  return (
    <main className="grid min-h-dvh place-items-center bg-bg px-6 text-fg">
      <div className="w-full max-w-xs text-center">
        <div className="mx-auto grid h-16 w-16 place-items-center rounded-3xl bg-primary text-on-primary">
          <Zap className="h-8 w-8" />
        </div>
        <h1 className="mt-4 font-display text-4xl">ТОК</h1>
        <p className="mt-2 text-sm text-muted">Доход на линии</p>
        <div className="mt-6 h-1.5 overflow-hidden rounded-full bg-surface-highest">
          <div className="loadbar h-full w-1/3 rounded-full bg-primary" />
        </div>
      </div>
    </main>
  );
}

function Shell() {
  const [tab, setTab] = useState<Tab>("today");
  const [editor, setEditor] = useState<Editor | null>(null);
  const game = useGame();
  const name = useTok((s) => s.name);
  const bike = useTok((s) => s.bike);
  const titleId = useTok((s) => s.titleId);
  const shifts = useTok((s) => s.shifts);
  const sound = useTok((s) => s.sound);
  const fanfare = useTok((s) => s.fanfare);
  const dismissFanfare = useTok((s) => s.dismissFanfare);
  const snack = useTok((s) => s.snack);
  const dismissSnack = useTok((s) => s.dismissSnack);
  const addShift = useTok((s) => s.addShift);
  const updateShift = useTok((s) => s.updateShift);
  const deleteShift = useTok((s) => s.deleteShift);
  const pct = game.level.span > 0 ? game.level.into / game.level.span : 0;

  useEffect(() => {
    if (!snack) return;
    const t = window.setTimeout(dismissSnack, 2600);
    return () => window.clearTimeout(t);
  }, [snack, dismissSnack]);

  function openEdit(shift: Shift) {
    const { id, createdAt: _c, demo: _d, ...draft } = shift;
    setEditor({ mode: "edit", id, draft });
  }

  const tabTitle = TABS.find((t) => t.id === tab)?.label ?? "";

  return (
    <div className="min-h-dvh bg-bg text-fg">
      <div className="mx-auto grid min-h-dvh max-w-6xl lg:grid-cols-[272px_minmax(0,1fr)]">
        <aside className="sticky top-0 hidden h-dvh flex-col gap-4 border-r border-outline-soft bg-surface-low p-4 lg:flex">
          <div className="flex items-center gap-3">
            <BikeMark bike={bike} />
            <div>
              <div className="font-display text-2xl leading-none">ТОК</div>
              <div className="text-xs text-muted">доход на линии</div>
            </div>
          </div>
          <nav className="flex flex-col gap-1">
            {TABS.map((item) => (
              <NavButton key={item.id} item={item} active={tab === item.id} onClick={() => setTab(item.id)} wide />
            ))}
          </nav>
          <div className="mt-auto rounded-3xl bg-surface-high p-4">
            <div className="text-sm text-muted">Уровень {game.level.level}</div>
            <div className="font-display text-xl">{game.rank.name}</div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-highest">
              <div className="h-full rounded-full bg-primary" style={{ width: `${Math.round(pct * 100)}%` }} />
            </div>
            <p className="mt-2 text-xs text-muted">{formatNum(game.coins)} монет</p>
          </div>
        </aside>
        <div className="flex min-h-dvh min-w-0 flex-col">
          <header className="sticky top-0 z-20 bg-bg px-4 pt-4 pb-3">
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1 lg:hidden">
                <div className="flex items-center gap-3">
                  <BikeMark bike={bike} className="h-11 w-11" />
                  <div className="min-w-0">
                    <div className="truncate font-semibold">{name}</div>
                    <div className="truncate text-xs text-muted">
                      {game.rank.name}
                      {titleName(titleId) ? ` · ${titleName(titleId)}` : ""}
                    </div>
                  </div>
                </div>
              </div>
              <h1 className="hidden flex-1 font-display text-3xl lg:block">{tabTitle}</h1>
              <div className="flex items-center gap-2">
                <span className="inline-flex min-h-11 items-center gap-1 rounded-full bg-surface-high px-3 text-sm font-semibold">
                  <Flame className={cn("h-4 w-4 text-tertiary", game.streak >= 2 && "flame")} />
                  {game.streak}
                </span>
                <span className="inline-flex min-h-11 items-center rounded-full bg-tertiary-container px-3 text-sm font-semibold text-on-tertiary-container">
                  {formatNum(game.coins)}
                </span>
              </div>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-highest" aria-hidden>
              <div className="h-full rounded-full bg-primary" style={{ width: `${Math.round(pct * 100)}%`, transition: "width 400ms cubic-bezier(0.2,0,0,1)" }} />
            </div>
            <p className="mt-1 text-xs text-muted">
              Ур. {game.level.level} · ещё {formatNum(Math.max(0, game.level.span - game.level.into))} XP
              {game.streak > 0 ? ` · серия ${game.streak} ${plural(game.streak, "день", "дня", "дней")}` : ""}
            </p>
          </header>
          <main className="min-w-0 flex-1 px-4 pt-1 pb-28 lg:pb-10">
            {tab === "today" ? (
              <TodayView onCreate={() => setEditor({ mode: "create", draft: emptyToday() })} onEdit={openEdit} onEndLive={(draft) => setEditor({ mode: "create", draft })} />
            ) : null}
            {tab === "stats" ? <StatsView /> : null}
            {tab === "path" ? <PathView /> : null}
            {tab === "me" ? <ProfileView onEdit={openEdit} /> : null}
          </main>
        </div>
      </div>
      <nav className="nav-pad fixed inset-x-0 bottom-0 z-30 border-t border-outline-soft bg-surface-low lg:hidden">
        <div className="mx-auto grid max-w-lg grid-cols-4 px-2 pt-1">
          {TABS.map((item) => (
            <NavButton key={item.id} item={item} active={tab === item.id} onClick={() => setTab(item.id)} />
          ))}
        </div>
      </nav>
      <Sheet
        open={editor != null}
        title={editor?.mode === "edit" ? "Править смену" : "Новая смена"}
        onClose={() => setEditor(null)}
      >
        {editor ? (
          <ShiftForm
            key={editor.mode === "edit" ? editor.id : `${editor.draft.date}-${editor.draft.hours}-${editor.draft.orders}`}
            initial={editor.draft}
            zones={knownZones(shifts)}
            submitLabel={editor.mode === "edit" ? "Сохранить" : "В копилку"}
            onDelete={
              editor.mode === "edit"
                ? () => {
                    deleteShift(editor.id);
                    setEditor(null);
                  }
                : undefined
            }
            onSubmit={(draft) => {
              if (editor.mode === "edit") {
                const res = updateShift(editor.id, draft);
                if (res?.levels.length) playSound("level", sound);
                else playSound("tap", sound);
              } else {
                const res = addShift(draft);
                playSound(res.levels.length ? "level" : res.achievements.length ? "win" : "coin", sound);
              }
              setEditor(null);
            }}
          />
        ) : null}
      </Sheet>
      {fanfare ? <FanfareModal fanfare={fanfare} onClose={dismissFanfare} /> : null}
      {snack ? (
        <div className="fixed bottom-24 left-1/2 z-40 max-w-xs -translate-x-1/2 rounded-full bg-fg px-4 py-3 text-center text-sm font-semibold text-bg lg:bottom-8">{snack}</div>
      ) : null}
    </div>
  );
}

function NavButton({
  item,
  active,
  onClick,
  wide,
}: {
  item: (typeof TABS)[number];
  active: boolean;
  onClick: () => void;
  wide?: boolean;
}) {
  const Icon = item.icon;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "tap flex min-h-12 items-center justify-center gap-2 rounded-full text-xs font-semibold",
        wide && "justify-start px-4 text-sm",
        active ? "bg-primary-container text-on-primary-container" : "text-muted",
      )}
      aria-current={active ? "page" : undefined}
    >
      <Icon className="h-5 w-5" />
      {item.label}
    </button>
  );
}

function emptyToday(): ShiftDraft {
  const now = new Date();
  const h = now.getHours();
  const slot = h >= 6 && h < 11 ? "morning" : h >= 11 && h < 17 ? "day" : h >= 17 && h < 22 ? "evening" : "night";
  return {
    date: todayISO(now),
    slot,
    hours: 0,
    orders: 0,
    earned: 0,
    tips: 0,
    km: 0,
    zone: "",
    note: "",
    mood: 4,
  };
}
