
// @ts-check

/**
 * @typedef {[
*   gx: number,
*   gy: number,
*   gz: number,
*   r: number,
*   g: number,
*   b: number
* ]} SensorData */

/**
* @param {Float32Array} arr
* @returns {SensorData}
*/
export function parseSensorData(arr) {
 if (arr.length !== 6) {
   throw new Error(`Expected 6 values for sensor data, got ${arr.length}`);
 }
 return /** @type {SensorData} */ ([arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]]);
}
