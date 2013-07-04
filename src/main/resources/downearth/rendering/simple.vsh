#version 150

in vec4 a_position;

in vec3 a_instance_position;
in float a_instance_scale;

uniform mat4 u_mvp;

// out vec3 v_pos;

void main() {
    vec4 pos = vec4(a_instance_position,0) + a_position * vec4( a_instance_scale, a_instance_scale, a_instance_scale, 1);
    gl_Position = u_mvp * pos;
}


