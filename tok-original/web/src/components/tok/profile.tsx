import { useState } from "react";
import { MOODS, formatHours, formatMonth, formatNum, formatRub, knownZones, perHour, plural, relativeDay, slotLabel, type Shift } from "@/game/model";
import { useGame, useTok } from "@/game/store";
import { Button, Card, Field, Seg, fieldClass } from "@/components/tok/ui";

export function ProfileView({ onEdit }: { onEdit: (shift: Shift) => void }) {
  const shifts = useTok((s) => s.shifts);
  const name = useTok((s) => s.name);
  const setName = useTok((s) => s.setName);
  const goals = useTok((s) => s.goals);
  const setGoals = useTok((s) => s.setGoals);
  const sound = useTok((s) => s.sound);
  const setSound = useTok((s) => s.setSound);
  const theme = useTok((s) => s.theme);
  const setTheme = useTok((s) => s.setTheme);
  const orderAuto = useTok((s) => s.orderAuto);
  const setOrderAuto = useTok((s) => s.setOrderAuto);
  const loadDemo = useTok((s) => s.loadDemo);
  const clearDemo = useTok((s) => s.clearDemo);
  const clearAll = useTok((s) => s.clearAll);
  const deleteShift = useTok((s) => s.deleteShift);
  const game = useGame();
  const [draftName, setDraftName] = useState(name);
  const [auto, setAuto] = useState(String(orderAuto || ""));
  const [wipe, setWipe] = useState(false);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const sorted = [...shifts].sort((a, b) => b.date.localeCompare(a.date) || b.createdAt - a.createdAt);
  const groups: { month: string; items: Shift[] }[] = [];
  for (const shift of sorted) {
    const month = formatMonth(shift.date);
    const last = groups[groups.length - 1];
    if (!last || last.month !== month) groups.push({ month, items: [shift] });
    else last.items.push(shift);
  }
  const hours = shifts.reduce((a, s) => a + s.hours, 0);
  const earned = shifts.reduce((a, s) => a + s.earned, 0);

  return (
    <div className="flex flex-col gap-4">
      <div className="rise">
        <h1 className="font-display text-3xl">Ты</h1>
        <p className="text-sm text-muted">
          {formatNum(shifts.length)} {plural(shifts.length, "смена", "смены", "смен")} · {formatHours(hours)} · {formatRub(earned)}
        </p>
      </div>
      <Card className="rise rise-1">
        <Field label="Имя на линии">
          <input
            className={fieldClass}
            value={draftName}
            maxLength={20}
            onChange={(e) => setDraftName(e.target.value)}
            onBlur={() => setName(draftName)}
          />
        </Field>
        <p className="mt-3 text-xs text-muted">
          Серия {game.streak} · рекорд серии {game.maxStreak}. Значки за серию смотрят на рекорд, а не на сегодняшний огонь.
        </p>
      </Card>
      <Card>
        <h2 className="mb-3 font-semibold">Цели</h2>
        <div className="flex flex-col gap-3">
          <Stepper label="День, ₽" value={goals.dailyRub} step={100} min={500} max={50000} format={formatRub} onChange={(dailyRub) => setGoals({ ...goals, dailyRub })} />
          <Stepper label="Заказы в день" value={goals.dailyOrders} step={1} min={1} max={80} format={(n) => formatNum(n)} onChange={(dailyOrders) => setGoals({ ...goals, dailyOrders })} />
          <Stepper label="Часы в день" value={goals.dailyHours} step={0.5} min={1} max={16} format={formatHours} onChange={(dailyHours) => setGoals({ ...goals, dailyHours })} />
          <Stepper label="Неделя, ₽" value={goals.weeklyRub} step={500} min={3000} max={300000} format={formatRub} onChange={(weeklyRub) => setGoals({ ...goals, weeklyRub })} />
        </div>
        <p className="mt-3 text-xs text-muted">Кольца и часть квестов смотрят сюда. Значки завязаны на свои пороги, так что занижать цель ради значка бесполезно.</p>
      </Card>
      <Card>
        <h2 className="mb-3 font-semibold">Ощущения</h2>
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-semibold">Звук</div>
            <div className="text-xs text-muted">Короткий сигнал на заказ, награду и уровень</div>
          </div>
          <Button variant={sound ? "filled" : "outline"} onClick={() => setSound(!sound)} aria-pressed={sound}>
            {sound ? "Вкл" : "Выкл"}
          </Button>
        </div>
        <div className="mt-4">
          <div className="mb-2 text-sm font-semibold">Тема</div>
          <Seg value={theme} onChange={(id) => setTheme(id === "light" ? "light" : "dark")} options={[{ id: "dark", label: "Ночь" }, { id: "light", label: "День" }]} />
        </div>
        <div className="mt-4">
          <Field label="Автосумма заказа" hint="В живой смене кнопка +1 прибавит эти рубли. 0 — только счётчик.">
            <input className={fieldClass} inputMode="numeric" value={auto} placeholder="0" onChange={(e) => setAuto(e.target.value)} onBlur={() => { const n=Number(auto.replace(",",".")); const safe=Number.isFinite(n)?n:0; setOrderAuto(safe); setAuto(safe?String(safe):""); }} />
          </Field>
        </div>
      </Card>
      <section className="flex flex-col gap-3">
        <h2 className="font-semibold">История</h2>
        {groups.length === 0 ? <p className="text-sm text-muted">Когда появится первая смена, она встанет сюда. Нажатие открывает правку.</p> : null}
        {groups.map((group) => (
          <div key={group.month} className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold text-muted">{group.month}</h3>
            {group.items.map((shift) => {
              const rate = perHour(shift.earned, shift.hours);
              const mood = MOODS.find((m) => m.id === shift.mood)?.label;
              return (
                <Card key={shift.id}>
                  <button type="button" className="tap w-full text-left" onClick={() => onEdit(shift)}>
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="font-semibold">{relativeDay(shift.date)} · {slotLabel(shift.slot)}{shift.demo ? " · демо" : ""}</div>
                        <div className="text-xs text-muted">{formatNum(shift.orders)} зак. · {formatHours(shift.hours)}{rate != null ? ` · ${formatRub(rate)}/ч` : ""}{shift.zone ? ` · ${shift.zone}` : ""}{mood ? ` · ${mood}` : ""}</div>
                        {shift.note ? <div className="mt-1 text-sm">{shift.note}</div> : null}
                      </div>
                      <div className="font-display text-xl text-tertiary">{formatRub(shift.earned)}</div>
                    </div>
                  </button>
                  {confirmId === shift.id ? (
                    <div className="mt-3 flex gap-2">
                      <Button variant="danger" className="flex-1" onClick={() => { deleteShift(shift.id); setConfirmId(null); }}>Удалить</Button>
                      <Button variant="outline" className="flex-1" onClick={() => setConfirmId(null)}>Нет</Button>
                    </div>
                  ) : <button type="button" className="mt-2 text-xs font-semibold text-danger" onClick={() => setConfirmId(shift.id)}>Удалить</button>}
                </Card>
              );
            })}
          </div>
        ))}
      </section>
      <Card>
        <h2 className="mb-2 font-semibold">Данные</h2>
        <p className="mb-3 text-sm text-muted">Всё лежит только на этом устройстве. Демо не затирает твои смены.</p>
        <div className="flex flex-col gap-2">
          <Button variant="tonal" onClick={loadDemo}>{shifts.some((s) => s.demo) ? "Обновить демо" : "Загрузить демо-район"}</Button>
          {shifts.some((s) => s.demo) ? <Button variant="outline" onClick={clearDemo}>Убрать только демо</Button> : null}
          {wipe ? <Button variant="danger" onClick={() => { clearAll(); setDraftName("Курьер"); setWipe(false); }}>Точно стереть всё</Button> : <Button variant="text" className="text-danger" onClick={() => setWipe(true)}>Сбросить все данные</Button>}
        </div>
        {knownZones(shifts).length > 0 ? <p className="mt-3 text-xs text-muted">Районы в записях: {knownZones(shifts).join(", ")}</p> : null}
      </Card>
    </div>
  );
}

function Stepper({label,value,step,min,max,format,onChange}:{label:string;value:number;step:number;min:number;max:number;format:(n:number)=>string;onChange:(n:number)=>void}){const nudge=(dir:number)=>{const next=Math.round((value+dir*step)*100)/100;onChange(Math.min(max,Math.max(min,next)))};return <div className="flex items-center justify-between gap-3"><div><div className="text-sm font-semibold">{label}</div><div className="font-display text-xl">{format(value)}</div></div><div className="flex gap-2"><Button variant="outline" className="w-12 px-0" onClick={()=>nudge(-1)}>−</Button><Button variant="outline" className="w-12 px-0" onClick={()=>nudge(1)}>+</Button></div></div>}
