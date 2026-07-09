---
description: Uniformar finales de linea y eliminar la advertencia "LF will be replaced by CRLF" en Git (Windows)
---

# Uniformar finales de linea (evitar la advertencia CRLF de Git)

Objetivo: que el repo almacene texto en **LF**, que los scripts de Windows
(`.ps1`/`.bat`/`.cmd`) queden en **CRLF**, y que Git deje de mostrar
`warning: ... LF will be replaced by CRLF the next time Git touches it`.

## Contexto / por que aparece la advertencia

Git avisa cuando el final de linea del *working copy* NO coincide con lo que
almacenara/entregara segun `.gitattributes` (o `core.autocrlf`). Al fijar las
reglas con `.gitattributes` y sincronizar el working tree, el aviso desaparece.

## Pasos

1. Crear (o revisar) `.gitattributes` en la raiz del repo con este contenido:

```gitattributes
# Normalizar finales de linea: LF en el repo para todo texto
* text=auto eol=lf

# Scripts especificos de Windows que requieren CRLF
*.ps1 text eol=crlf
*.bat text eol=crlf
*.cmd text eol=crlf

# Binarios (no tocar)
*.ttf binary
*.otf binary
*.png binary
*.jpg binary
*.jpeg binary
*.gif binary
*.webp binary
*.ico binary
*.apk binary
*.aab binary
*.keystore binary
*.jar binary
```

Ajustar la lista de binarios segun los tipos de archivo del repo.

2. Versionar `.gitattributes` y renormalizar el indice:

// turbo
```powershell
git add .gitattributes; git add --renormalize .; git status --short
```

3. Commit (solo si hay algo staged), siguiendo la **disciplina de commits del workflow `conventions`**
   (se propone y se espera confirmacion; no marcar `// turbo`):

```powershell
git commit -m "Add .gitattributes to normalize line endings (LF in repo, CRLF for Windows scripts)"
```

4. Verificar el estado de finales de linea de un archivo de cada tipo.
   El formato es `i/<indice> w/<working> attr/<atributo> ruta`; NO debe haber
   desajuste (p.ej. `eol=crlf` con `w/lf`):

// turbo
```powershell
git ls-files --eol README.md verify-compile.ps1
```

5. Si algun archivo muestra desajuste (working != lo que pide el atributo, tipico
   en `.ps1` que quedan en LF cuando deben ser CRLF), forzar su re-checkout para
   que el working copy se regenere con el EOL correcto (seguro: debe estar ya
   commiteado / sin cambios sin guardar):

```powershell
Remove-Item <archivo>; git checkout -- <archivo>; git ls-files --eol <archivo>
```

6. Verificacion final: el arbol debe quedar limpio y sin advertencias en las
   operaciones normales de Git:

// turbo
```powershell
git status --short
```

## Notas

- El repositorio guarda texto en **LF** aunque el working copy en Windows tenga
  CRLF; eso es lo esperado y correcto.
- No fuerza la conversion de binarios (marcados como `binary`).
- Si el aviso aparece una unica vez tras crear `.gitattributes`, ejecutar el
  paso 5 sobre el archivo afectado lo elimina definitivamente.
