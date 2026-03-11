theorem id_prop (A : Prop) : A -> A := by
  intro h
  exact h

theorem and_intro (A B : Prop) : A -> B -> A And B := by
  intro hA
  intro hB
  exact And.intro hA hB

