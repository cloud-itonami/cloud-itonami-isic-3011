# cloud-itonami-isic-3011

Open Business Blueprint for **ISIC Rev.5 3011**: building of ships and
floating structures -- hull-block fabrication, weld/NDT screening and
class-evidence issuance for a community shipyard.

This repository publishes a shipbuilding actor -- block intake,
per-jurisdiction class-rules verification, NDT-defect screening, robot
block-dispatch and class-evidence finalization -- as an OSS business
that any qualified shipyard can fork, deploy, run, improve and sell,
so a yard keeps its own construction and class history instead of
renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Shipyard Advisor ⊣
Shipyard Governor**.

## Scope note: manufacturing, not ship operation

This repository is scoped to **building** ships and floating
structures (hull blocks, modules, weld/NDT, class evidence). It is
not a ship-operator vertical (navigation, crewing, commercial
voyage). Distinct from:

- `cloud-itonami-isic-3020` — railway rolling-stock **manufacturing**
- `cloud-itonami-isic-3030` — aircraft/aerospace **manufacturing**
- transport-operator ISICs (e.g. 5011 sea passenger / 5020 sea freight)

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (weld, fit-up,
inspection, NDT scan) operate under an actor that proposes actions and
an independent **Shipyard Governor** that gates them. The governor
never issues class evidence itself; `:high`/`:safety-critical`
actions (`:actuation/dispatch-block`, `:actuation/issue-class-evidence`)
require human sign-off.

## Core contract

```text
block intake + class-rules verify + NDT screen
  -> Shipyard Advisor proposal
  -> Shipyard Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Dispatching a weld/fit-up robot and issuing class evidence produce
**unsigned draft records and ledger facts only**. This actor does not
talk to real yard control systems or class-society portals. Signature
and hardware dispatch are the shipyard's own acts.

## Ops

| Op | Effect |
|---|---|
| `:block/intake` | normalize block directory patch (phase 3 may auto-commit when clean) |
| `:class-rules/verify` | per-jurisdiction class evidence checklist (always human) |
| `:ndt/screen` | NDT defect screen (HARD hold if unresolved) |
| `:actuation/dispatch-block` | draft block-dispatch record (always human) |
| `:actuation/issue-class-evidence` | draft class-evidence record (always human) |

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.
