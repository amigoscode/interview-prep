package com.amigoscode.karat.equipment;

/** A reservable item at the facility, such as a treadmill or a rowing machine. */
public class Equipment {
    private final int equipmentId;
    private final String name;

    public Equipment(int equipmentId, String name) {
        this.equipmentId = equipmentId;
        this.name = name;
    }

    public int getEquipmentId() {
        return equipmentId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Equipment ID: " + equipmentId + ", Name: " + name;
    }
}
