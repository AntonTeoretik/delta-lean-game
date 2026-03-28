def topLevelId (n : Nat) : Nat :=
  n

namespace Demo

open Nat
set_option pp.universes true

def insideNamespace (n : Nat) : Nat :=
  n + 1

section LocalFacts

variable (A : Prop)

theorem localTheorem : A -> A := by
  intro h
  exact h

end LocalFacts

end Demo
