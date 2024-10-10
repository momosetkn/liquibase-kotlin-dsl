package momosetkn.utils

private val changeSetRegex = Regex("""changeSet\(author = "(.+)", id = "(\d+)-(\d)"\) \{""")

fun String.maskChangeSetParams() =
    this.replace(changeSetRegex, "changeSet(author = \"**********\", id = \"*************-\$3\") {")
