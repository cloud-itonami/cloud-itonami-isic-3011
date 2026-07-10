# Business Model: Building of Ships and Floating Structures

## Classification
- Repository: `cloud-itonami-isic-3011`
- ISIC Rev.5: `3011` — building of ships and floating structures — hull-block fabrication, weld/NDT and class evidence
- Social impact: maritime-safety, supply-resilience, industrial-jobs

## Customer
- independent shipyards and block fabricators needing auditable class and production records
- contract yards producing hull blocks, modules and floating structures for multiple owners
- shipowners and fleet managers needing verifiable build and NDT history for procured tonnage
- flag-state authorities and class societies needing verifiable construction evidence
- programs that cannot accept closed, unauditable shipyard MES / quality platforms

## Offer
- class-rules and jurisdiction-scope version management
- robotics-assisted weld, assembly and non-destructive-testing inspection records
- block dimensional-tolerance and NDT chain-of-custody history
- class-evidence drafts and disclosure records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per yard / production line
- support retainer with SLA
- weld/NDT robot integration and maintenance

## Trust Controls
- out-of-spec blocks are blocked; class evidence is mandatory for release paths; block history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive design and production data stays outside Git
- a fabricated class-rules citation, incomplete evidence, an out-of-spec
  block tolerance, or an unresolved NDT defect -- each forces a hold,
  not an override
- class-evidence issuance is logged and escalated, and cannot be
  finalized twice for the same block: a double-issuance attempt is
  held off this actor's own block facts alone, with no upstream
  comparison needed
