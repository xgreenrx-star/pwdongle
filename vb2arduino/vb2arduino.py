import re
from dataclasses import dataclass, field
from typing import List


@dataclass
class TranspileResult:
    cpp: str


class VBTranspiler:
    """Minimal VB6-like to Arduino C++ transpiler for a safe subset."""

    def __init__(self) -> None:
        self.global_lines: List[str] = []
        self.setup_lines: List[str] = []
        self.loop_lines: List[str] = []
        self.block_stack: List[str] = []

    def transpile(self, source: str) -> TranspileResult:
        self.global_lines.clear()
        self.setup_lines.clear()
        self.loop_lines.clear()
        self.block_stack.clear()

        current = None  # None, "setup", "loop"
        for raw in source.splitlines():
            line = raw.strip()
            if not line or line.startswith("'"):
                continue

            upper = line.upper()
            if upper.startswith("CONST "):
                self.global_lines.append(self._emit_const(line))
                continue
            if upper.startswith("DIM "):
                self.global_lines.append(self._emit_dim(line))
                continue
            if upper.startswith("SUB SETUP"):
                current = "setup"
                continue
            if upper.startswith("SUB LOOP"):
                current = "loop"
                continue
            if upper == "END SUB":
                current = None
                continue

            target = self._target_lines(current)
            target.append(self._emit_statement(line))

        cpp = self._render_cpp()
        return TranspileResult(cpp=cpp)

    def _target_lines(self, current: str | None) -> List[str]:
        if current == "setup":
            return self.setup_lines
        if current == "loop":
            return self.loop_lines
        return self.global_lines

    def _emit_const(self, line: str) -> str:
        # Const LED = 2
        m = re.match(r"CONST\s+(\w+)\s*=\s*(.+)", line, re.IGNORECASE)
        if not m:
            return f"// TODO const: {line}"
        name, value = m.groups()
        return f"const auto {name} = {self._expr(value)};"

    def _emit_dim(self, line: str) -> str:
        # Dim x As Integer  | Dim x
        m = re.match(r"DIM\s+(\w+)(?:\s+AS\s+(\w+))?", line, re.IGNORECASE)
        if not m:
            return f"// TODO dim: {line}"
        name, type_token = m.groups()
        c_type = self._map_type(type_token)
        return f"{c_type} {name} = 0;"

    def _emit_statement(self, line: str) -> str:
        upper = line.upper()

        # Control flow
        if upper.startswith("IF "):
            m = re.match(r"IF\s+(.+?)\s+THEN", line, re.IGNORECASE)
            cond = self._expr(m.group(1)) if m else "/*cond*/"
            self.block_stack.append("if")
            return f"if ({cond}) {{"
        if upper.startswith("ELSEIF "):
            m = re.match(r"ELSEIF\s+(.+?)\s+THEN", line, re.IGNORECASE)
            cond = self._expr(m.group(1)) if m else "/*cond*/"
            return f"}} else if ({cond}) {{"
        if upper == "ELSE":
            return "} else {"
        if upper in ("END IF", "ENDIF"):
            if self.block_stack and self.block_stack[-1] == "if":
                self.block_stack.pop()
            return "}"

        if upper.startswith("FOR "):
            m = re.match(r"FOR\s+(\w+)\s*=\s*(.+)\s+TO\s+(.+)", line, re.IGNORECASE)
            if m:
                var, start, end = m.groups()
                self.block_stack.append("for")
                return f"for (int {var} = {self._expr(start)}; {var} <= {self._expr(end)}; ++{var}) {{"
        if upper.startswith("NEXT"):
            if self.block_stack and self.block_stack[-1] == "for":
                self.block_stack.pop()
            return "}"

        # I/O helpers
        io_patterns = [
            (r"PINMODE\s+(\w+),\s*(\w+)", lambda m: f"pinMode({m.group(1)}, {m.group(2)});"),
            (r"DIGITALWRITE\s+(\w+),\s*(\w+)", lambda m: f"digitalWrite({m.group(1)}, {m.group(2)});"),
            (r"DELAY\s+(.+)", lambda m: f"delay({self._expr(m.group(1))});"),
            (r"ANALOGWRITE\s+(\w+),\s*(.+)", lambda m: f"analogWrite({m.group(1)}, {self._expr(m.group(2))});"),
            (r"SERIALBEGIN\s+(.+)", lambda m: f"Serial.begin({self._expr(m.group(1))});"),
            (r"SERIALPRINTLINE\s+(.+)", lambda m: f"Serial.println({self._expr(m.group(1))});"),
            (r"SERIALPRINT\s+(.+)", lambda m: f"Serial.print({self._expr(m.group(1))});"),
        ]
        for pat, fn in io_patterns:
            m = re.match(pat, line, re.IGNORECASE)
            if m:
                return fn(m)

        # Assignments: x = expr
        m_assign = re.match(r"(\w+)\s*=\s*(.+)", line)
        if m_assign:
            lhs, rhs = m_assign.groups()
            return f"{lhs} = {self._expr(rhs)};"

        return f"// TODO: {line}"

    def _expr(self, expr: str) -> str:
        expr = expr.strip()
        # Logical replacements
        expr = re.sub(r"\bAND\b", "&&", expr, flags=re.IGNORECASE)
        expr = re.sub(r"\bOR\b", "||", expr, flags=re.IGNORECASE)
        expr = re.sub(r"\bNOT\b", "!", expr, flags=re.IGNORECASE)
        # Comparisons
        expr = expr.replace("<>", "!=")
        # Function mappings
        expr = re.sub(r"\bDIGITALREAD\s*\(([^)]+)\)", r"digitalRead(\1)", expr, flags=re.IGNORECASE)
        expr = re.sub(r"\bANALOGREAD\s*\(([^)]+)\)", r"analogRead(\1)", expr, flags=re.IGNORECASE)
        expr = re.sub(r"\bSERIALAVAILABLE\s*\(\)", "Serial.available()", expr, flags=re.IGNORECASE)
        expr = re.sub(r"\bSERIALREAD\s*\(\)", "Serial.read()", expr, flags=re.IGNORECASE)
        # Strings already VB-style quotes, leave as-is
        return expr

    def _map_type(self, token: str | None) -> str:
        if not token:
            return "int"
        t = token.lower()
        if t in ("integer", "long"):
            return "int"
        if t in ("byte",):
            return "uint8_t"
        if t in ("boolean",):
            return "bool"
        if t in ("single", "double"):
            return "float"
        if t in ("string",):
            return "String"
        return "int"

    def _render_cpp(self) -> str:
        globals_section = "\n".join(self.global_lines)
        setup_section = "\n    ".join(self.setup_lines) if self.setup_lines else ""
        loop_section = "\n    ".join(self.loop_lines) if self.loop_lines else ""
        return f"""#include <Arduino.h>

{globals_section}

void setup() {{
    {setup_section}
}}

void loop() {{
    {loop_section}
}}
"""


def transpile_string(source: str) -> str:
    return VBTranspiler().transpile(source).cpp
