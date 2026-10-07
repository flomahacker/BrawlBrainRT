import { useMemo, useState } from "react";
import { BarChart3, Bike, CalendarDays, Coins, Flame, Plus, Route, Settings2, Trash2, Trophy, UserRound, X, Zap } from "lucide-react";
import { cn } from "@/lib/cn";

type Tab = "today" | "stats" | "path" | "me";
type Shift = {
  id: string;
  date: string;
  hours: number;
  orders: number;
  earned: number;
  tips: number;
  km: number;
  zone: string;
  note: string;
  mood: number;
};

const KEY = "tok-apk-data-v1";
const tabs: { id: Tab; label: string; icon: typeof Zap }[] = [
  { id: "today", label: "Смена", icon: Zap },
  { id: "stats", label: "Разбор", icon: BarChart3 },
  { id: "path", label: "Путь", icon: Route },
  { id: "me", label: "Ты", icon: UserRound },
];

const money = (n: number) => new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 }).format(Math.round(n)) + " ₽";
const num = (n: number) => new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 1 }).format(n);
const today = () => new Date().toISOString().slice(0, 10);

function readShifts(): Shift[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed?.shifts) ? parsed.shifts : [];
  } catch {
    return [];
  }
}

export function AppLite() {
  const [tab, setTab] = useState<Tab>("today");
  const [theme, setTheme] = useState<"dark" | "light">(() => {
    try { return localStorage.getItem(KEY + ":theme") === "light" ? "light" : "dark"; } catch { return "dark"; }
  });
  const [name, setName] = useState(() => {
    try { return localStorage.getItem(KEY + ":name") || "Курьер"; } catch { return "Курьер"; }
  });
  const [shifts, setShifts] = useState<Shift[]>(readShifts);
  const [formOpen, setFormOpen] = useState(false);

  const total = useMemo(() => ({
    hours: shifts.reduce((a, s) => a + s.hours, 0),
    orders: shifts.reduce((a, s) => a + s.orders, 0),
    earned: shifts.reduce((a, s) => a + s.earned, 0),
    km: shifts.reduce((a, s) => a + s.km, 0),
  }), [shifts]);

  const level = Math.max(1, Math.floor(total.earned / 5000) + 1);
  const xp = total.earned % 5000;
  const streak = useMemo(() => {
    const dates = new Set(shifts.map(s => s.date));
    let d = new Date();
    let count = 0;
    while (dates.has(d.toISOString().slice(0,10))) {
      count++;
      d.setDate(d.getDate() - 1);
    }
    return count;
  }, [shifts]);

  const save = (next: Shift[]) => {
    setShifts(next);
    try { localStorage.setItem(KEY, JSON.stringify({ shifts: next })); } catch {}
  };

  const addShift = (s: Omit<Shift, "id">) => {
    save([{ ...s, id: crypto.randomUUID() }, ...shifts]);
    setFormOpen(false);
  };

  const deleteShift = (id: string) => save(shifts.filter(s => s.id !== id));

  const loadDemo = () => {
    const base = new Date();
    const demo = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(base);
      d.setDate(d.getDate() - i);
      return {
        id: crypto.randomUUID(),
        date: d.toISOString().slice(0,10),
        hours: 6 + (i % 3) * 0.5,
        orders: 18 + i * 2,
        earned: 1800 + i * 170,
        tips: 80 + i * 15,
        km: 35 + i * 3,
        zone: ["Центр", "Север", "Вокзал"][i % 3],
        note: i === 1 ? "Дождь" : "",
        mood: 4 - (i % 2),
      };
    });
    save(demo);
    setTab("today");
  };

  const clearAll = () => save([]);

  return (
    <div data-theme={theme} className="min-h-dvh bg-bg text-fg">
      <div className="mx-auto min-h-dvh max-w-5xl lg:grid lg:grid-cols-[250px_minmax(0,1fr)]">
        <aside className="hidden h-dvh sticky top-0 flex-col border-r border-outline-soft bg-surface-low p-4 lg:flex">
          <div className="flex items-center gap-3">
            <BikeMark />
            <div><div className="font-display text-2xl leading-none">ТОК</div><div className="text-xs text-muted">доход на линии</div></div>
          </div>
          <nav className="mt-8 flex flex-col gap-1">
            {tabs.map(t => <TabButton key={t.id} item={t} active={tab === t.id} onClick={() => setTab(t.id)} wide />)}
          </nav>
          <div className="mt-auto rounded-3xl bg-surface-high p-4">
            <div className="text-xs text-muted">Уровень {level}</div>
            <div className="mt-1 font-display text-xl">{level < 4 ? "Новичок" : level < 8 ? "Ровный курьер" : "Топ линии"}</div>
            <div className="mt-3 h-2 rounded-full bg-surface-highest"><div className="h-full rounded-full bg-primary" style={{width:`${xp/50}%`}} /></div>
            <div className="mt-2 text-xs text-muted">{money(xp)} XP до уровня выше</div>
          </div>
        </aside>

        <main className="min-w-0 px-4 pb-28 lg:px-7 lg:pb-10">
          <header className="sticky top-0 z-20 bg-bg/95 backdrop-blur px-0 pt-4 pb-3">
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1 lg:hidden"><div className="flex items-center gap-3"><BikeMark small /><div className="min-w-0"><div className="truncate font-semibold">{name}</div><div className="text-xs text-muted">Уровень {level}</div></div></div></div>
              <h1 className="hidden flex-1 font-display text-3xl lg:block">{tabs.find(t => t.id === tab)?.label}</h1>
              <span className="inline-flex min-h-11 items-center gap-1 rounded-full bg-surface-high px-3 text-sm font-semibold"><Flame className="h-4 w-4 text-tertiary" />{streak}</span>
              <span className="inline-flex min-h-11 items-center rounded-full bg-tertiary-container px-3 text-sm font-semibold text-on-tertiary-container">{money(total.earned)}</span>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-highest"><div className="h-full rounded-full bg-primary" style={{width:`${Math.min(100, xp/50)}%`}} /></div>
          </header>

          {tab === "today" && <Today shifts={shifts} total={total} onCreate={() => setFormOpen(true)} onDelete={deleteShift} />}
          {tab === "stats" && <Stats shifts={shifts} total={total} />}
          {tab === "path" && <Path total={total} level={level} />}
          {tab === "me" && <Profile name={name} setName={(v) => { setName(v); try { localStorage.setItem(KEY + ":name", v); } catch {} }} theme={theme} setTheme={(v) => { setTheme(v); try { localStorage.setItem(KEY + ":theme", v); } catch {} }} onDemo={loadDemo} onClear={clearAll} shifts={shifts} onDelete={deleteShift} />}
        </main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-outline-soft bg-surface-low/95 backdrop-blur lg:hidden">
        <div className="mx-auto grid max-w-lg grid-cols-4 px-2 pb-[max(8px,env(safe-area-inset-bottom))] pt-1">
          {tabs.map(t => <TabButton key={t.id} item={t} active={tab === t.id} onClick={() => setTab(t.id)} />)}
        </div>
      </nav>

      {formOpen && <ShiftSheet onClose={() => setFormOpen(false)} onSubmit={addShift} />}
    </div>
  );
}

function BikeMark({ small=false }: { small?: boolean }) {
  return <div className={cn("relative grid place-items-center rounded-2xl bg-primary text-on-primary", small ? "h-11 w-11" : "h-12 w-12")}><Bike className="h-6 w-6" /><span className="absolute -right-1 -top-1 grid h-5 w-5 place-items-center rounded-full bg-tertiary text-on-tertiary"><Zap className="h-3 w-3" /></span></div>;
}

function TabButton({ item, active, onClick, wide=false }: { item: typeof tabs[number]; active: boolean; onClick: () => void; wide?: boolean }) {
  const Icon = item.icon;
  return <button type="button" onClick={onClick} className={cn("tap min-h-12 rounded-full px-3 text-sm font-semibold", wide ? "flex w-full items-center justify-start gap-3" : "flex flex-col items-center justify-center gap-0.5 text-[11px]", active ? "bg-primary-container text-on-primary-container" : "text-muted")}><Icon className={wide ? "h-5 w-5" : "h-5 w-5"} />{item.label}</button>;
}

function Today({ shifts, total, onCreate, onDelete }: { shifts: Shift[]; total: any; onCreate: () => void; onDelete: (id: string) => void }) {
  const latest = shifts[0];
  return <div className="space-y-4">
    <section className="rise grid gap-3 sm:grid-cols-2">
      <div className="rounded-3xl bg-primary-container p-5 text-on-primary-container"><div className="text-sm opacity-80">Заработано</div><div className="mt-1 font-display text-4xl">{money(total.earned)}</div><div className="mt-3 text-sm">{num(total.orders)} заказов · {num(total.hours)} ч на линии</div></div>
      <div className="rounded-3xl bg-surface-high p-5"><div className="text-sm text-muted">Темп</div><div className="mt-1 font-display text-4xl">{total.hours ? money(total.earned/total.hours) : "0 ₽"}</div><div className="mt-3 text-sm text-muted">средний ₽/ч за всё время</div></div>
    </section>
    <section className="rounded-3xl bg-surface-high p-4">
      <div className="flex items-center justify-between gap-3"><div><div className="font-semibold">Сегодня</div><div className="text-xs text-muted">Добавь смену — приложение посчитает темп, серию и прогресс.</div></div><button className="tap grid h-12 w-12 place-items-center rounded-full bg-primary text-on-primary" onClick={onCreate} aria-label="Добавить смену"><Plus /></button></div>
      {latest ? <div className="mt-4 rounded-2xl bg-surface-low p-4"><div className="flex items-start justify-between gap-4"><div><div className="font-semibold">{latest.date} · {latest.zone || "без района"}</div><div className="mt-1 text-sm text-muted">{latest.orders} зак. · {num(latest.hours)} ч · {money(latest.earned/latest.hours)}/ч</div>{latest.note && <div className="mt-2 text-sm">{latest.note}</div>}</div><button onClick={() => onDelete(latest.id)} className="text-muted" aria-label="Удалить смену"><Trash2 className="h-5 w-5" /></button></div></div> : <div className="mt-4 rounded-2xl border border-dashed border-outline p-5 text-center text-sm text-muted">Смен пока нет.<br/>Первая запись станет базой для статистики.</div>}
    </section>
    <section><h2 className="mb-2 font-semibold">Последние смены</h2><div className="space-y-2">{shifts.slice(0,8).map(s => <div key={s.id} className="rounded-2xl bg-surface-high p-4"><div className="flex items-center justify-between gap-3"><div><div className="font-semibold">{s.date} · {s.orders} зак. · {num(s.hours)} ч</div><div className="text-xs text-muted">{s.zone || "без района"}{s.mood ? ` · настроение ${s.mood}/5` : ""}</div></div><div className="font-display text-xl text-tertiary">{money(s.earned)}</div></div></div>)}</div></section>
  </div>;
}

function Stats({ shifts, total }: { shifts: Shift[]; total: any }) {
  const daily = useMemo(() => shifts.slice().sort((a,b)=>a.date.localeCompare(b.date)).slice(-10), [shifts]);
  const max = Math.max(1, ...daily.map(s => s.earned));
  return <div className="space-y-4">
    <section className="grid gap-3 sm:grid-cols-3"><Metric icon={<Coins />} label="Доход" value={money(total.earned)} /><Metric icon={<PackageIcon />} label="Заказы" value={num(total.orders)} /><Metric icon={<Route />} label="Километры" value={num(total.km)} /></section>
    <section className="rounded-3xl bg-surface-high p-4"><div className="mb-4 flex items-center justify-between"><div><h2 className="font-display text-xl">Динамика</h2><div className="text-xs text-muted">Последние 10 записей</div></div><BarChart3 className="text-muted" /></div><div className="space-y-3">{daily.map(s => <div key={s.id}><div className="mb-1 flex justify-between text-xs text-muted"><span>{s.date}</span><span>{money(s.earned)}</span></div><div className="h-3 rounded-full bg-surface-low"><div className="h-full rounded-full bg-primary" style={{width:`${Math.max(4, s.earned/max*100)}%`}}/></div></div>)}</div></section>
    <section className="rounded-3xl bg-surface-high p-4"><h2 className="font-semibold">Что видно</h2><div className="mt-3 grid gap-2 sm:grid-cols-3"><Insight title="Средний чек" value={total.orders ? money(total.earned/total.orders) : "0 ₽"} /><Insight title="Средний час" value={total.hours ? money(total.earned/total.hours) : "0 ₽"} /><Insight title="Км на заказ" value={total.orders ? num(total.km/total.orders) : "0"} /></div></section>
  </div>;
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) { return <div className="rounded-3xl bg-surface-high p-4"><div className="flex items-center gap-2 text-muted">{icon}<span className="text-sm">{label}</span></div><div className="mt-2 font-display text-2xl">{value}</div></div>; }
function Insight({ title, value }: { title: string; value: string }) { return <div className="rounded-2xl bg-surface-low p-3"><div className="text-xs text-muted">{title}</div><div className="mt-1 font-display text-xl">{value}</div></div>; }
function PackageIcon(){ return <span className="inline-block h-5 w-5 rounded-md border-2 border-current" />; }

function Path({ total, level }: { total: any; level: number }) {
  const goals = [{ label: "Первая смена", done: total.hours > 0, value: "1 запись" }, { label: "100 заказов", done: total.orders >= 100, value: `${Math.min(100,total.orders)}/100` }, { label: "10 000 ₽", done: total.earned >= 10000, value: money(Math.min(10000,total.earned)) }, { label: "50 часов", done: total.hours >= 50, value: `${num(Math.min(50,total.hours))}/50 ч` }];
  return <div className="space-y-4">
    <section className="rounded-3xl bg-primary-container p-5 text-on-primary-container"><div className="text-sm opacity-80">Твой прогресс</div><div className="mt-1 font-display text-4xl">Уровень {level}</div><div className="mt-4 h-2 rounded-full bg-black/20"><div className="h-full rounded-full bg-current" style={{width:`${Math.min(100,(total.earned%5000)/50)}%`}} /></div></section>
    <section className="space-y-2">{goals.map((g,i)=><div key={g.label} className={cn("flex items-center gap-3 rounded-2xl p-4", g.done ? "bg-primary-container text-on-primary-container" : "bg-surface-high")}><div className="grid h-10 w-10 place-items-center rounded-full bg-surface-low"><Trophy className="h-5 w-5" /></div><div className="min-w-0 flex-1"><div className="font-semibold">{g.label}</div><div className="text-xs opacity-75">{g.value}</div></div><div className="text-xs font-bold">{g.done ? "ГОТОВО" : `#${i+1}`}</div></div>)}</section>
  </div>;
}

function Profile({ name, setName, theme, setTheme, onDemo, onClear, shifts, onDelete }: { name:string; setName:(v:string)=>void; theme:"dark"|"light"; setTheme:(v:"dark"|"light")=>void; onDemo:()=>void; onClear:()=>void; shifts:Shift[]; onDelete:(id:string)=>void }) {
  const [confirm, setConfirm] = useState(false);
  return <div className="space-y-4">
    <section className="rounded-3xl bg-surface-high p-4"><div className="flex items-center gap-3"><BikeMark /><div><h2 className="font-display text-2xl">Ты</h2><div className="text-sm text-muted">Личная статистика и настройки</div></div></div><label className="mt-4 block text-sm font-semibold">Имя на линии<input className={field} value={name} maxLength={24} onChange={e=>setName(e.target.value)} /></label></section>
    <section className="rounded-3xl bg-surface-high p-4"><div className="flex items-center gap-2 font-semibold"><Settings2 className="h-5 w-5 text-primary"/>Настройки</div><div className="mt-4 grid grid-cols-2 gap-2"><button className={cn(choice, theme==="dark"&&"bg-primary-container text-on-primary-container")} onClick={()=>setTheme("dark")}>Ночь</button><button className={cn(choice, theme==="light"&&"bg-primary-container text-on-primary-container")} onClick={()=>setTheme("light")}>День</button></div></section>
    <section className="rounded-3xl bg-surface-high p-4"><div className="font-semibold">Данные</div><div className="mt-1 text-sm text-muted">Хранятся локально на устройстве.</div><div className="mt-3 grid gap-2"><button className={button} onClick={onDemo}>Загрузить демо</button>{confirm ? <button className={cn(button,"bg-danger-container text-on-danger-container")} onClick={()=>{onClear();setConfirm(false)}}>Точно стереть всё</button> : <button className={cn(button,"text-danger")} onClick={()=>setConfirm(true)}>Стереть все данные</button>}</div></section>
    <section><h2 className="mb-2 font-semibold">История ({shifts.length})</h2><div className="space-y-2">{shifts.slice(0,12).map(s=><div key={s.id} className="rounded-2xl bg-surface-high p-4"><div className="flex items-start justify-between gap-3"><div><div className="font-semibold">{s.date} · {money(s.earned)}</div><div className="text-xs text-muted">{s.orders} зак. · {num(s.hours)} ч · {s.zone || "без района"}</div></div><button className="text-muted" onClick={()=>onDelete(s.id)} aria-label="Удалить"><Trash2 className="h-5 w-5"/></button></div></div>)}</div></section>
  </div>;
}

function ShiftSheet({ onClose, onSubmit }: { onClose:()=>void; onSubmit:(s:Omit<Shift,"id">)=>void }) {
  const [hours,setHours]=useState("6");
  const [orders,setOrders]=useState("20");
  const [earned,setEarned]=useState("2000");
  const [tips,setTips]=useState("0");
  const [km,setKm]=useState("30");
  const [zone,setZone]=useState("Центр");
  const [note,setNote]=useState("");
  return <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"><button className="scrim absolute inset-0" onClick={onClose} aria-label="Закрыть"/><div className="relative z-10 max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-surface p-4 sm:rounded-3xl"><div className="mb-3 flex items-center justify-between"><h2 className="font-display text-xl">Новая смена</h2><button className="text-muted" onClick={onClose} aria-label="Закрыть"><X/></button></div><div className="grid gap-3 sm:grid-cols-2"><label className="text-sm font-semibold">Дата<input className={field} type="date" defaultValue={today()} id="tok-date"/></label><label className="text-sm font-semibold">Часы<input className={field} inputMode="decimal" value={hours} onChange={e=>setHours(e.target.value)}/></label><label className="text-sm font-semibold">Заказы<input className={field} inputMode="numeric" value={orders} onChange={e=>setOrders(e.target.value)}/></label><label className="text-sm font-semibold">Сумма, ₽<input className={field} inputMode="decimal" value={earned} onChange={e=>setEarned(e.target.value)}/></label><label className="text-sm font-semibold">Чаевые, ₽<input className={field} inputMode="decimal" value={tips} onChange={e=>setTips(e.target.value)}/></label><label className="text-sm font-semibold">Км<input className={field} inputMode="decimal" value={km} onChange={e=>setKm(e.target.value)}/></label><label className="text-sm font-semibold sm:col-span-2">Район<input className={field} value={zone} onChange={e=>setZone(e.target.value)}/></label><label className="text-sm font-semibold sm:col-span-2">Заметка<input className={field} value={note} onChange={e=>setNote(e.target.value)} placeholder="Дождь, лифт, хороший вечер"/></label></div><button className={cn(button,"mt-4 w-full bg-primary text-on-primary")} onClick={()=>{ const d=document.getElementById("tok-date") as HTMLInputElement; onSubmit({date:d.value || today(),hours:Math.max(0,Number(hours.replace(",","."))||0),orders:Math.max(0,Math.round(Number(orders)||0)),earned:Math.max(0,Number(earned.replace(",","."))||0),tips:Math.max(0,Number(tips.replace(",","."))||0),km:Math.max(0,Number(km.replace(",","."))||0),zone,note,mood:4}); }}>Сохранить смену</button></div></div>;
}

const field = "mt-1 min-h-12 w-full rounded-2xl border border-outline-soft bg-surface-low px-3 text-base text-fg outline-none";
const button = "min-h-12 rounded-2xl bg-surface-low px-4 text-sm font-semibold";
const choice = "min-h-12 rounded-full bg-surface-low px-4 text-sm font-semibold";
