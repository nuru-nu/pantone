
// @ts-check

/**
 * @typedef {[
 *   t:  number,
 *   i:  number,
 *   gx: number,
 *   gy: number,
 *   gz: number,
 *   rz: number,
 *   r:  number,
 *   g:  number,
 *   b:  number
 * ]} SensorData */

/**
 * Parses a a SensorData buffer in big-endian format
 * @param {ArrayBuffer} buffer - Buffer containing one uint32 followed by float32 values
 * @returns {SensorData} Parsed data
 * @throws {Error} If buffer length is incorrect
 */
export function parseSensorData(buffer) {
  const expectedBytes = 4 + 1 + (7 * 4);
  if (buffer.byteLength !== expectedBytes) {
    throw new Error(`Buffer must be exactly ${expectedBytes} bytes (got ${buffer.byteLength})`);
  }

  const dataView = new DataView(buffer);
  const result = new Array(9);

  result[0] = dataView.getUint32(0, false);
  result[1] = dataView.getUint8(4);

  for (let i = 0; i < 7; i++) {
    result[i + 2] = dataView.getFloat32(5 + (i * 4), false);
  }

  return /** @type {SensorData} */ (result);
}
