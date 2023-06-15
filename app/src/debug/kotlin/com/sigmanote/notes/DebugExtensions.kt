



inline fun debugCheck(value: Boolean, message: () -> String = { "Check failed" }) {
    check(value, message)
}

inline fun debugRequire(value: Boolean, message: () -> String = { "Failed requirement" }) {
    require(value, message)
}
