package DGU_AI_LAB.admin_be.domain.groups.entity;

/** 공용 그룹 작업의 종류. config-server 작업의 op 값은 {@link #op()}다. */
public enum GroupOperationKind {
    CREATE, ADD, REMOVE;

    public String op() {
        return name().toLowerCase();
    }
}
