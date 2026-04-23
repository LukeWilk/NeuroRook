# Iteration notes

- The original Graphs configuration separated channel and dataset filters, which made it harder to express per-channel graph visibility.
- Prevent this on future runs by checking whether the requested layout implies a state-model change, not just a visual rearrangement.
- For table-like UI requests, verify whether the interactions should become per-cell selections before editing only the composables.
- When a matrix-style list feels too large, review column widths, row spacing, and typography together before changing the underlying state model again.
- For matrix filters, check early whether users also need row-level and column-level bulk actions so the state helpers can be designed once.
- In Compose UI tests for compact screens, prefer assertions that do not depend on long lists staying fully visible after layout tightening.
- When a feature mirrors hardware-controlled entities, treat the hardware enablement state as the source of truth for what can be selected, not just for initial defaults.
- When disabled hardware entities should not be actionable or informative in a secondary feature, prefer hiding them entirely instead of rendering disabled rows.
- If every mirrored hardware entity is unavailable, prefer a dedicated guidance message over a generic "waiting for data" state so users know which page to act on.
- When swapping a text control for a chevron or other icon-only affordance, keep the old wording as the accessibility label so tests and assistive tech still describe the action clearly.
- After replacing a text action with an icon-only toggle, tighten the surrounding spacers and icon-button size together; shrinking only one of them often leaves the card looking oddly loose.










