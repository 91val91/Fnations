'use strict';
// Coordonnées Maven -> chemin de fichier ("org.ow2.asm:asm:9.7" -> org/ow2/asm/asm/9.7/asm-9.7.jar)
function mavenPath(name) {
  const [coord, ext0] = name.split('@');
  const parts = coord.split(':');
  const [group, artifact, version] = parts;
  const classifier = parts[3];
  const ext = ext0 || 'jar';
  const file = `${artifact}-${version}${classifier ? '-' + classifier : ''}.${ext}`;
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${file}`;
}

/** Clé de déduplication groupe:artefact[:classifier] */
function libKey(name) {
  const parts = name.split('@')[0].split(':');
  return parts[0] + ':' + parts[1] + (parts[3] ? ':' + parts[3] : '');
}

module.exports = { mavenPath, libKey };
