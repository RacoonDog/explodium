# Explodium

A Fabric mod designed to push the limits of Minecraft tnt chained explosion performance.

Requires [lithium](https://modrinth.com/mod/lithium).

# How does it work?

## Preface

Before understanding how you can optimize explosions, you first have to understand how explosions are implemented.

Each explosion has an associated `power` value. For example:
- TNT: `4`
- Creepers: `3`
- Charged Creepers: `6`
- Ghast fireballs: `1`
- Wither skull projectiles: `1`

The computation behind explosions can be divided into three distinct tasks:
1. `getBlocksToDestroy()`
2. `damageEntities()`
3. `destroyBlocks()`

### `getBlocksToDestroy()`

The algorithm for calculating which blocks around the explosion should be destroyed is fairly simple:  
- You cast `16 <sup>3</sup> - 14<sup>3</sup>`, or 1352 rays. (Consider a cubic volume of 16 x 16 x 16 points centered around the explosion's origin. The points that are on the faces of the "cube" define the direction of individual rays.)
- Each ray starts with a randomly chosen "strength" value between `0.7 * power` and `1.3 * power`.
- The ray begins at the explosion's origin and moves outward in `0.3` block steps until its strength reaches zero.
- At each step, block at the ray's current position is queried and, if the block has any blast resistance, the ray's strength is reduced by `(blastResistance + 0.3) * 0.3`.
- If the remaining strength is above zero, the position of the block is added to the list of blocks to destroy.
- You then reduce the ray's strength by `0.225` regardless of the block.

This means a standard TNT explosion's rays traverse at most 24 steps (`ceil((4 * 1.3) / 0.225)`), or 7.2 blocks.

### `damageEntities()`

The algorithm for determining how much damage entities take from an explosion is quite complex, so here’s a simplified version:

- You create a cube of side length `power * 2 + 2` centered on the explosion's origin.
- For each entity in that region, you discard those immune to explosions and those further than `power * 2` blocks away from the origin.
- You then calculate how much "cover" that entity has from the explosion:
  - Consider a set of points inside the entity's hitbox such that every point is less than 0.5 blocks from any adjacent point[^1].
  - Cast rays from every point to the explosion's origin.
  - The `cover` is the ratio of rays that **dont** intersect any blocks to the total number of rays cast.
- The entity is knocked back `cover * (1 - distance)` blocks, where `distance` is the entity's distance from the explosion origin.
- The entity takes an amount of damage equal to...
  ```java
  public double getExplosionDamage(double distance, double power, double cover) {
      double impact = (1 - distance / (2 * power)) * cover;
      return damage = (impact * impact + impact) * 7 * power + 1;
  }
  ```

[^1]: The exact formula for calculating the horizontal distance between two adjacent points is `width / (width * 2 + 1)` (`height` for vertical distance.)

### `destroyBlocks()`

This one is quite shrimple 🦐:

- Shuffle the list of block positions marked for destruction.
- Destroy each block and add its drops to a list.
- Spawn item entities for each drop at the corresponding position.