#pragma once

#include <cstdio>

namespace cheevos {

// Lets rcheevos hash disc games kept as CHD (read with libchdr); other discs still use rcheevos' own reader. Every
// file rcheevos reads is opened with [open] (null: fopen), so games streamed from their folder can be hashed too.
void installChdReader(FILE *(*open)(const char *path));

} // namespace cheevos
