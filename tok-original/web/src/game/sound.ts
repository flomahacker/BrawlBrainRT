let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!ctx) ctx = new AudioContext();
  return ctx;
}

function tone(
  audioCtx: AudioContext,
  freq: number,
  at: number,
  dur: number,
  type: OscillatorType,
  gain: number,
  endFreq?: number,
) {
  const osc = audioCtx.createOscillator();
  const amp = audioCtx.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, at);
  if (endFreq) osc.frequency.exponentialRampToValueAtTime(endFreq, at + dur);
  amp.gain.setValueAtTime(gain, at);
  amp.gain.exponentialRampToValueAtTime(0.0008, at + dur);
  osc.connect(amp);
  amp.connect(audioCtx.destination);
  osc.start(at);
  osc.stop(at + dur + 0.02);
}

export function playSound(kind: "tap" | "coin" | "win" | "level", enabled: boolean) {
  if (!enabled) return;
  const audioCtx = audio();
  if (!audioCtx) return;
  void audioCtx.resume();
  const now = audioCtx.currentTime;
  if (kind === "tap") tone(audioCtx, 540, now, 0.07, "sine", 0.035);
  else if (kind === "coin") tone(audioCtx, 680, now, 0.16, "triangle", 0.045, 1040);
  else if (kind === "win") tone(audioCtx, 520, now, 0.22, "sine", 0.05, 780);
  else {
    tone(audioCtx, 440, now, 0.28, "triangle", 0.05, 880);
    tone(audioCtx, 660, now + 0.12, 0.32, "triangle", 0.04, 1320);
  }
}
