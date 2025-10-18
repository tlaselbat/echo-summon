# ClickMC: Echo Summon — Saddle Summon Tool Guide

The Saddle Summon Tool lets you carry a mount inside an item, summon it wherever you need it, dismiss it, or release it back into the world.


## Supported mounts
- Horse
- Donkey
- Mule
- Camel
- Skeleton Horse
- Zombie Horse
- Happy Ghast (special case: uses a harness under-the-hood)


## Getting the item
- Creative inventory: Tools tab.
- Stack size: 1. Fireproof.


## How to use
- Capture (store a mount)
  - Hold the Saddle Summon Tool.
  - Right-click an allowed mount while not riding and while the tool is empty.
  - The mount will be stored in the tool. For horses, the tool ensures the mount is tame.

- Summon (bring the stored mount to you and mount it)
  - If a mount is stored and you are not riding, right-click with the tool.
  - The mount spawns at your position, is saddled automatically, and you begin riding it.

- Dismiss (remove a summoned mount)
  - While riding a mount you summoned with the tool, right-click with the tool to dismiss it.
  - The mount is removed but remains stored in the tool.

- Release (spawn the stored mount into the world and clear the tool)
  - Sneak (Shift) + Right-click with the tool.
  - The stored mount appears nearby and the tool is emptied. The released mount is not kept saddled by the tool.


## Tips & details
- One at a time: The tool can store exactly one mount.
- Cooldown: 1 second between actions to prevent spam.
- Tool icon: Changes to reflect the stored mount.
- Tooltips: Hover the item and hold Shift to see details like owner, health, and ID.
- Taming: Horses captured/summoned by the tool are marked tame so you can ride and see the saddle.
- No re-capturing summoned mounts: You cannot capture a mount that was summoned by the tool; release first, then capture a wild/unsummoned one.
- Happy Ghast: Treated as a harness mount internally. The tool handles the harness automatically when capturing/summoning; you don't need to equip one manually for basic use.


## Common messages and what they mean
- "This summon tool already contains a mount." → Release the mount (Sneak + Right-click) if you want to store a different one.
- "Cannot store a summoned mount" → You’re trying to capture a mount that was spawned by a tool. Release it first, then capture a naturally-spawned (or otherwise unsummoned) mount.
- "No mount stored in summon tool" → The tool is empty; capture a mount first.
- "Already riding" → Dismount or dismiss your current mount before summoning another.


## Troubleshooting
- Nothing happens on right-click
  - Make sure you’re holding the tool and not on cooldown (wait 1s).
  - For capture: ensure the tool is empty and the target is a supported mount.
  - For summon: ensure a mount is stored and you are not riding.
- Mount releases without a saddle
  - Expected: when you release a stored mount, the tool removes its special saddle/harness so the mount isn’t considered tool-managed anymore.


## Optional (operators)
- A server/operator command exists to spawn a test lineup of supported mounts for quick testing:
  - `/echo_summon test` (permission level 2).
