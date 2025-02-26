package io.github.racoondog.explodium;

public interface IExplosion {
    int explodium$getOriginSectionX();
    int explodium$getOriginSectionY();
    int explodium$getOriginSectionZ();
    boolean explodium$isOriginSectionEmpty();
    boolean explodium$isSectionEmpty(int sectionX, int sectionY, int sectionZ);
}
