package DGU_AI_LAB.admin_be.global.validation;

import java.util.Set;

/**
 * 사용자 컨테이너 이미지·계정 원장이 이미 쥔 리눅스 계정·그룹 이름.
 * 이 이름으로 우분투 계정을 만들면 원장에 같은 이름의 사용자가 있거나(USER_ALREADY_EXISTS),
 * 개인 그룹이 이미지 그룹과 부딪혀(primary group conflict) 승인 뒤에야 계정 생성이 실패한다 —
 * 등록 시점에 막는다.
 *
 * <p>출처: config-server {@code base_etc/passwd} 시드의 사용자 이름 + {@code main.py}의
 * {@code RESERVED_GROUP_NAMES} + 운영용 계정(admin·ubuntu·svmanager·ailab-krb5).
 * 이미지를 바꾸면 두 곳을 다시 뽑아 함께 갱신한다.
 */
public final class ReservedLinuxNames {

    private static final Set<String> NAMES = Set.of(
            // base_etc/passwd 시드
            "root", "daemon", "bin", "sys", "sync", "games", "man", "lp", "mail", "news", "uucp", "proxy",
            "www-data", "backup", "list", "irc", "_apt", "nobody", "systemd-network", "systemd-timesync",
            "messagebus", "polkitd",
            // RESERVED_GROUP_NAMES 중 위에 없는 것
            "_ssh", "adm", "audio", "cdrom", "crontab", "dialout", "dip", "disk", "docker", "fax", "floppy",
            "gnats", "input", "kmem", "kvm", "nogroup", "nova", "operator", "plugdev", "render", "sasl",
            "shadow", "src", "ssh", "ssl-cert", "staff", "sudo", "svmanager", "systemd-journal",
            "systemd-resolve", "tape", "tty", "users", "utmp", "video", "voice",
            // 운영용 계정
            "admin", "ubuntu", "ailab-krb5"
    );

    private ReservedLinuxNames() {
    }

    public static boolean contains(String name) {
        return name != null && NAMES.contains(name);
    }
}
