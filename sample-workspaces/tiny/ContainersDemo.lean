def topLevelValue : Nat :=
  10

namespace Geometry

set_option pp.universes true
open Nat

def pointCount : Nat :=
  3

section Internal

variable (A : Prop)

theorem keepTrue : A -> A := by
  intro h
  exact h

end

end

def afterNamespace : Nat :=
  topLevelValue + 1
