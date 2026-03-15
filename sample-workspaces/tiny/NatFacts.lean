def addOne (n : Nat) : Nat :=
  n + 1
  someNonsence

theorem addOne_ge (n : Nat) : addOne n >= n := by
  unfold addOne
  exact Nat.le_add_right n 1