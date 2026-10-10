package xyz.zcraft.asteroid.ncm.data;

public record Album(
        long publishTime,
        int size,
        Artist artist,
        long copyrightId,
        String name,
        long id,
        long picId,
        long mark,
        int status
) {
}
