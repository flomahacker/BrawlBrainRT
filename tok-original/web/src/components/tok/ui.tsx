import { useEffect, useState, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/cn";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "filled" | "tonal" | "outline" | "text" | "danger" | "inverse";
};

const variants: Record<NonNullable<ButtonProps["variant"]>, string> = {
  filled: "bg-primary text-on-primary",
  tonal: "bg-primary-container text-on-primary-container",
  outline: "bg-transparent text-fg border border-outline",
  text: "bg-transparent text-primary",
  danger: "bg-danger-container text-on-danger-container",
  inverse: "bg-on-primary-container text-primary-container",
};

export function Button({ variant = "filled", className, type = "button", ...props }: ButtonProps) {
  return <button type={type} className={cn("tap inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-4 text-sm font-semibold disabled:opacity-45", variants[variant], className)} {...props} />;
}
export function Card({ className, children, tone = "default" }: { className?: string; children: ReactNode; tone?: "default" | "primary" | "tertiary" | "danger" }) {
  return <section className={cn("rounded-3xl p-4", tone === "default" && "bg-surface-high text-fg", tone === "primary" && "bg-primary-container text-on-primary-container", tone === "tertiary" && "bg-tertiary-container text-on-tertiary-container", tone === "danger" && "bg-danger-container text-on-danger-container", className)}>{children}</section>;
}
export function Ring({ value, max, label, display, tone = "primary" }: { value:number; max:number; label:string; display:string; tone?:"primary"|"tertiary"|"secondary" }) {
 const r=26,c=2*Math.PI*r,pct=max<=0?0:Math.max(0,Math.min(1,value/max));
 const stroke=tone==="tertiary"?"var(--color-tertiary)":tone==="secondary"?"var(--color-secondary)":"var(--color-primary)";
 return <div className="flex flex-col items-center gap-1"><svg viewBox="0 0 68 68" className="h-16 w-16" role="img" aria-label={`${label}: ${display}`}><circle cx="34" cy="34" r={r} fill="none" stroke="var(--color-outline-soft)" strokeWidth="6"/><circle cx="34" cy="34" r={r} fill="none" stroke={stroke} strokeWidth="6" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c*(1-pct)} transform="rotate(-90 34 34)" style={{transition:"stroke-dashoffset 500ms cubic-bezier(0.2,0,0,1)"}}/><text x="34" y="37" textAnchor="middle" fill="currentColor" fontSize="10" fontFamily="var(--font-sans)" fontWeight="700">{Math.round(pct*100)}</text></svg><div className="text-center text-xs font-semibold">{display}</div><div className="text-center text-xs text-muted">{label}</div></div>;
}
export function Meter({ value, max }: {value:number;max:number}) { const pct=max<=0?0:Math.max(0,Math.min(1,value/max)); return <div className="h-2 overflow-hidden rounded-full bg-surface-highest" aria-hidden><div className="h-full rounded-full bg-primary" style={{width:`${pct*100}%`,transition:"width 400ms cubic-bezier(0.2,0,0,1)"}}/></div>; }
export function Sheet({open,title,onClose,children}:{open:boolean;title:string;onClose:()=>void;children:ReactNode}) {
 useEffect(()=>{if(!open)return;const onKey=(e:KeyboardEvent)=>{if(e.key==="Escape")onClose()};document.addEventListener("keydown",onKey);const prev=document.body.style.overflow;document.body.style.overflow="hidden";return()=>{document.removeEventListener("keydown",onKey);document.body.style.overflow=prev}},[open,onClose]);
 if(!open)return null;
 return <div className="fixed inset-0 z-40 flex items-end justify-center sm:items-center"><button type="button" className="scrim absolute inset-0" aria-label="Закрыть" onClick={onClose}/><div role="dialog" aria-modal="true" aria-label={title} className="sheet-in relative z-10 max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-surface px-4 pt-3 pb-6 sm:rounded-3xl"><div className="mx-auto mb-3 h-1 w-10 rounded-full bg-outline sm:hidden"/><div className="mb-3 flex items-center justify-between gap-3"><h2 className="font-display text-xl">{title}</h2><Button variant="text" onClick={onClose} className="px-3">Закрыть</Button></div>{children}</div></div>;
}
export function Field({label,hint,error,children}:{label:string;hint?:string;error?:string;children:ReactNode}){return <label className="flex flex-col gap-1 text-sm"><span className="font-semibold">{label}</span>{children}{error?<span className="text-xs text-danger">{error}</span>:hint?<span className="text-xs text-muted">{hint}</span>:null}</label>}
export const fieldClass="min-h-12 w-full rounded-2xl border border-outline-soft bg-surface-low px-3 text-base text-fg outline-none";
export function CountUp({value,format}:{value:number;format:(n:number)=>string}){const[shown,setShown]=useState(value);useEffect(()=>{const from=shown,to=value;if(from===to)return;const start=performance.now();let raf=0;const tick=(now:number)=>{const t=Math.min(1,(now-start)/500),eased=1-(1-t)**3;setShown(from+(to-from)*eased);if(t<1)raf=requestAnimationFrame(tick)};raf=requestAnimationFrame(tick);return()=>cancelAnimationFrame(raf)},[value]);return <>{format(shown)}</>}
export function Seg({value,options,onChange}:{value:string;options:{id:string;label:string}[];onChange:(id:string)=>void}){return <div className="flex gap-1 rounded-full bg-surface-low p-1">{options.map(opt=><button key={opt.id} type="button" onClick={()=>onChange(opt.id)} className={cn("tap min-h-11 flex-1 rounded-full px-2 text-sm font-semibold",opt.id===value?"bg-secondary-container text-on-secondary-container":"text-muted")} aria-pressed={opt.id===value}>{opt.label}</button>)}</div>}
