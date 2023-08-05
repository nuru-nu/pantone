
export function div(classes) {
    const div = document.createElement('div');
    classes && div.setAttribute('class', classes);
    return div;
}