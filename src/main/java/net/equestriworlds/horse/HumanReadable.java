package net.equestriworlds.horse;

interface HumanReadable {
    String getHumanName();

    static String enumToHuman(Enum e) {
        String[] ns = e.name().split("_");
        StringBuilder sb = new StringBuilder(ns[0].substring(0, 1))
            .append(ns[0].substring(1).toLowerCase());
        for (int i = 1; i < ns.length; i += 1) {
            sb.append(" ")
                .append(ns[i].substring(0, 1))
                .append(ns[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
