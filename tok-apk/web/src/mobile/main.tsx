import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "@/components/tok/app";
import "@/styles.css";

const root = document.getElementById("root");

if (!root) {
  throw new Error("TOK root element is missing");
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
