package ru.iktk.modules.integration.model;

/**
 * Тест-фабрика для {@link Geometry}: {@code protected} no-arg конструктор
 * доступен только из пакета {@code model}, поэтому тесты из других пакетов
 * создают entity через этот класс.
 */
public final class GeometryTestFactory {

    private GeometryTestFactory() {
    }

    /** Создать пустую {@link Geometry} (все поля null/0/false). */
    public static Geometry newGeometry() {
        return new Geometry();
    }
}