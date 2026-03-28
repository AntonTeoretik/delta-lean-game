namespace Algebra

section LocalNat

variable (n : Nat)

def twice : Nat :=
  n + n

theorem twice_ge (n : Nat) : twice n >= n := by
  unfold twice
  exact Nat.le_add_right n n


end LocalNat

def one : Nat :=
  1

end Algebra

def outside : Nat :=
  5


mutual
  def foo (n : Nat) : Nat :=
    if h : n = 0 then 0 else bar (n - 1)
  termination_by n
  decreasing_by
    simp_wf
    exact Nat.sub_lt (Nat.pos_of_ne_zero h) (by decide)

  def bar (n : Nat) : Nat :=
    if h : n = 0 then 0 else foo (n - 1)
  termination_by n
  decreasing_by
    simp_wf
    exact Nat.sub_lt (Nat.pos_of_ne_zero h) (by decide)
end

#print foo
